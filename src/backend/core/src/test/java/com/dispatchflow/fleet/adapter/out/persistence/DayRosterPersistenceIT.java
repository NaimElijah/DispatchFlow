package com.dispatchflow.fleet.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.domain.RosterDefault;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.fleet.domain.roster.Availability;
import com.dispatchflow.fleet.domain.roster.DayRoster;
import com.dispatchflow.fleet.domain.roster.DayRosterSnapshot;
import com.dispatchflow.fleet.domain.roster.RosterEntry;
import com.dispatchflow.fleet.domain.roster.RosterVehicle;
import com.dispatchflow.fleet.domain.roster.Shift;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link DayRosterRepository} against a real PostgreSQL (ADR-126, ADR-127): what a date's roster
 * stores and loads as, with its entries and unavailable vehicles in order, its version, the first
 * save another call wins for a date, and the stored states the load check and the schema refuse.
 *
 * <p>The database is shared and never cleaned, and a date has one roster. So test {@code n} works
 * on its own dates ({@code dayOf(n)}, {@code dayOf(100 + n)}, …) and keys its own rosters.
 */
@IntegrationTest
class DayRosterPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_726f_7374L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2031, 1, 1);
    // Israel's clocks go forward at 02:00 on the Friday before March's last Sunday, and back at
    // 02:00 on October's last Sunday. No dayOf(n) a test uses falls on any of these three dates.
    private static final DeliveryDate SPRING_FORWARD = DeliveryDate.of(LocalDate.of(2031, 3, 28));
    private static final DeliveryDate FALL_BACK = DeliveryDate.of(LocalDate.of(2031, 10, 26));
    private static final DeliveryDate NEXT_SPRING_FORWARD = DeliveryDate.of(LocalDate.of(2032, 3, 26));
    private static final ShiftHours DAY = ShiftHours.of(LocalTime.of(8, 0), LocalTime.of(16, 0));
    private static final ShiftHours EARLY = ShiftHours.of(LocalTime.of(6, 0), LocalTime.of(14, 0));
    private static final AccountId DANA = driver(1);
    private static final AccountId OMER = driver(2);
    private static final AccountId NOA = driver(3);
    private static final VehicleId V1 = vehicle(1);
    private static final VehicleId V2 = vehicle(2);
    private static final VehicleId V3 = vehicle(3);
    private static final List<VehicleId> ALL_ACTIVE = List.of(V1, V2, V3);

    @Autowired
    private DayRosterRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    // --- round trips, mirroring the domain's restore round trips

    /** The states the domain's restore round trip covers, reached through commands. */
    enum Scenario {
        EMPTY,
        A_DRIVER_IN_EACH_STATE,
        NO_VEHICLE_AND_A_VEHICLE,
        UNAVAILABLE_VEHICLES,
        ON_THE_DAY_THE_CLOCKS_GO_FORWARD,
        ON_THE_DAY_THE_CLOCKS_GO_BACK
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenARoster_WhenAddedAndLoadedInAnotherTransaction_ThenEveryEntryComesBackInOrder(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        DeliveryDate date = switch (scenario) {
            case ON_THE_DAY_THE_CLOCKS_GO_FORWARD -> SPRING_FORWARD;
            case ON_THE_DAY_THE_CLOCKS_GO_BACK -> FALL_BACK;
            default -> dayOf(n);
        };
        DayRoster added = transactions.inTransaction(() -> {
            DayRoster roster = DayRoster.open(rosterId(n), date);
            switch (scenario) {
                case EMPTY -> {}
                case A_DRIVER_IN_EACH_STATE -> {
                    roster.fill(List.of(working(DANA, V1)), ALL_ACTIVE);
                    roster.changeShift(OMER, EARLY, Standing.ACTIVE);
                    roster.changeShift(NOA, DAY, Standing.ACTIVE);
                    roster.markDriverUnavailable(NOA);
                }
                case NO_VEHICLE_AND_A_VEHICLE -> {
                    roster.changeShift(DANA, DAY, Standing.ACTIVE);
                    roster.unassign(DANA);
                    roster.fill(List.of(working(OMER, V2)), ALL_ACTIVE);
                }
                case UNAVAILABLE_VEHICLES -> {
                    roster.fill(List.of(working(DANA, V1)), ALL_ACTIVE);
                    roster.markVehicleUnavailable(V3, Standing.ACTIVE);
                    roster.markVehicleUnavailable(V1, Standing.ACTIVE);
                }
                case ON_THE_DAY_THE_CLOCKS_GO_FORWARD -> roster.fill(List.of(working(DANA, V1)), ALL_ACTIVE);
                case ON_THE_DAY_THE_CLOCKS_GO_BACK -> roster.changeShift(DANA, DAY, Standing.ACTIVE);
            }
            repository.add(roster);
            return roster;
        });

        // Read outside the transaction, so a collection the load left out would fail here.
        DayRoster loaded = transactions.inTransaction(() -> repository.get(date).orElseThrow());

        assertThat(loaded.snapshot()).isEqualTo(added.snapshot());
        assertThat(loaded.date()).isEqualTo(date);
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.entryOf(DANA)).isEqualTo(added.entryOf(DANA));
        assertThat(loaded.isUnavailable(V1)).isEqualTo(added.isUnavailable(V1));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADateNobodyRostered_WhenItsRosterIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(dayOf(7)))).isEmpty();
    }

    @Test
    void GivenRostersOfTwoDates_WhenOneIsLoaded_ThenItIsThatDatesRoster() {
        int n = 8;
        transactions.inTransaction(() -> {
            DayRoster first = DayRoster.open(rosterId(n), dayOf(n));
            first.changeShift(DANA, DAY, Standing.ACTIVE);
            repository.add(first);
            DayRoster next = DayRoster.open(rosterId(100 + n), dayAfter(dayOf(n)));
            next.changeShift(OMER, EARLY, Standing.ACTIVE);
            repository.add(next);
        });

        DayRosterSnapshot loaded = transactions.inTransaction(
                () -> repository.get(dayAfter(dayOf(n))).orElseThrow().snapshot());

        assertThat(loaded.entries()).extracting(RosterEntry::driver).containsExactly(OMER);
    }

    // --- versions, changes in place and conflicts

    @Test
    void GivenAnAddedRoster_WhenAShiftIsSetAndThenAVehicleMarkedUnavailable_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 9;
        transactions.inTransaction(() -> repository.add(DayRoster.open(rosterId(n), dayOf(n))));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            DayRoster roster = repository.get(dayOf(n)).orElseThrow();
            roster.changeShift(DANA, DAY, Standing.ACTIVE);
            repository.save(roster);
        });
        long withDana = storedVersion(n);
        transactions.inTransaction(() -> {
            DayRoster roster = repository.get(dayOf(n)).orElseThrow();
            roster.markVehicleUnavailable(V2, Standing.ACTIVE);
            repository.save(roster);
        });

        assertThat(List.of(added, withDana, storedVersion(n))).containsExactly(0L, 1L, 2L);
    }

    @Test
    void GivenAStoredRoster_WhenAVehicleMovesWithARouteAndAnotherIsMarkedAvailable_ThenEachListKeepsItsOrder() {
        int n = 10;
        DeliveryDate date = dayOf(n);
        transactions.inTransaction(() -> {
            DayRoster roster = DayRoster.open(rosterId(n), date);
            roster.fill(List.of(working(DANA, V1)), ALL_ACTIVE);
            roster.changeShift(OMER, EARLY, Standing.ACTIVE);
            roster.unassign(OMER);
            roster.changeShift(NOA, DAY, Standing.ACTIVE);
            roster.markVehicleUnavailable(V1, Standing.ACTIVE);
            roster.markVehicleUnavailable(V2, Standing.ACTIVE);
            roster.markVehicleUnavailable(V3, Standing.ACTIVE);
            repository.add(roster);
        });

        transactions.inTransaction(() -> {
            DayRoster roster = repository.get(date).orElseThrow();
            roster.moveVehicleWithRoute(V1, DANA, OMER);
            roster.markVehicleAvailable(V2);
            repository.save(roster);
        });

        DayRosterSnapshot loaded = transactions.inTransaction(
                () -> repository.get(date).orElseThrow().snapshot());
        assertThat(loaded.entries())
                .containsExactly(
                        new RosterEntry(
                                DANA, Shift.on(date, DAY), new RosterVehicle.NoVehicle(), Availability.AVAILABLE),
                        new RosterEntry(
                                OMER, Shift.on(date, EARLY), new RosterVehicle.Assigned(V1), Availability.AVAILABLE),
                        new RosterEntry(NOA, Shift.on(date, DAY), new RosterVehicle.NotSet(), Availability.AVAILABLE));
        assertThat(loaded.unavailableVehicles()).containsExactly(V1, V3);
        assertThat(loaded.version()).isEqualTo(Version.of(1));
    }

    @Test
    void GivenTwoTransactionsOnOneRoster_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 11;
        transactions.inTransaction(() -> repository.add(DayRoster.open(rosterId(n), dayOf(n))));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    DayRoster mine = repository.get(dayOf(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        DayRoster theirs = repository.get(dayOf(n)).orElseThrow();
                        theirs.changeShift(DANA, DAY, Standing.ACTIVE);
                        repository.save(theirs);
                    });
                    mine.markVehicleUnavailable(V1, Standing.ACTIVE);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("DayRoster");
                    assertThat(stale.id()).isEqualTo(rosterId(n));
                });

        DayRosterSnapshot stored = transactions.inTransaction(
                () -> repository.get(dayOf(n)).orElseThrow().snapshot());
        assertThat(stored.version()).isEqualTo(Version.of(1));
        assertThat(stored.entries()).extracting(RosterEntry::driver).containsExactly(DANA);
        assertThat(stored.unavailableVehicles()).isEmpty();
    }

    @Test
    void
            GivenAnotherCallStoredADatesRosterFirst_WhenAnotherRosterOfThatDateIsAdded_ThenStaleVersionAndTheFirstStands() {
        int n = 12;

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    DayRoster mine = DayRoster.open(rosterId(n), dayOf(n));
                    mine.changeShift(DANA, DAY, Standing.ACTIVE);
                    transactions.inANewTransaction(() -> {
                        DayRoster theirs = DayRoster.open(rosterId(100 + n), dayOf(n));
                        theirs.changeShift(OMER, EARLY, Standing.ACTIVE);
                        repository.add(theirs);
                    });
                    repository.add(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("DayRoster");
                    assertThat(stale.id()).isEqualTo(rosterId(n));
                });

        DayRosterSnapshot stored = transactions.inTransaction(
                () -> repository.get(dayOf(n)).orElseThrow().snapshot());
        assertThat(stored.entries()).extracting(RosterEntry::driver).containsExactly(OMER);
        assertThat(jdbc.sql("SELECT id FROM fleet.day_roster WHERE date = ?")
                        .param(dayOf(n).value())
                        .query(UUID.class)
                        .single())
                .isEqualTo(rosterId(100 + n));
    }

    // --- the load check (ADR-127 decision 1), mirroring the domain's damaged snapshots

    static Stream<Arguments> damagedRows() {
        return Stream.of(
                Arguments.of(
                        "a driver appears twice",
                        1_001,
                        (Damage) rows -> rows.updateEntry(1, "driver = '" + DANA.value() + "'"),
                        "DayRoster"),
                Arguments.of(
                        "a vehicle has two drivers",
                        1_002,
                        (Damage) rows -> rows.updateEntry(1, "vehicle = '" + V1.value() + "'"),
                        "DayRoster"),
                Arguments.of(
                        "a shift lies on another date",
                        1_003,
                        (Damage) rows -> rows.updateEntry(0, "shift_date = shift_date + 1"),
                        "DayRoster"),
                Arguments.of(
                        "a vehicle is marked unavailable twice",
                        1_004,
                        (Damage) rows -> rows.updateUnavailable(1, "vehicle = '" + V1.value() + "'"),
                        "DayRoster"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredRosterBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String detail, int n, Damage damage, String subject) {
        transactions.inTransaction(() -> repository.add(twoDriversAndTwoUnavailableVehicles(n, dayOf(n))));

        damage.apply(new Rows(jdbc, n));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(dayOf(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    @Test
    void GivenAStoredShiftThatNowStartsInTheHourTheClockSkips_WhenLoaded_ThenStoredStateInvalidNamesTheShift() {
        int n = 1_005;
        transactions.inTransaction(() -> repository.add(twoDriversAndTwoUnavailableVehicles(n, NEXT_SPRING_FORWARD)));

        new Rows(jdbc, n).updateEntry(0, "starts_at = '02:30'");

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(NEXT_SPRING_FORWARD)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("Shift"));
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("an assigned vehicle that names none", 1_006, (Damage)
                        rows -> rows.updateEntry(0, "vehicle = NULL")),
                Arguments.of("a vehicle not set that names one", 1_007, (Damage)
                        rows -> rows.updateEntry(1, "vehicle_choice = 'NOT_SET'")),
                Arguments.of("an unknown availability", 1_008, (Damage)
                        rows -> rows.updateEntry(0, "availability = 'ON_LEAVE'")),
                Arguments.of("two entries in one place in the list", 1_009, (Damage)
                        rows -> rows.updateEntry(1, "position = 0")),
                Arguments.of("a second roster for the date", 1_010, (Damage) Rows::insertASecondRoster));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredRoster_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Damage damage) {
        transactions.inTransaction(() -> repository.add(twoDriversAndTwoUnavailableVehicles(n, dayOf(n))));
        Rows rows = new Rows(jdbc, n);

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(dayOf(n)))).isPresent();
    }

    /** One damage done with SQL to a stored roster. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run on test {@code n}'s roster; every assignment is constant SQL. */
    record Rows(JdbcClient jdbc, int n) {

        void updateEntry(int position, String assignments) {
            jdbc.sql("UPDATE fleet.roster_entry SET " + assignments + " WHERE day_roster_id = ? AND position = ?")
                    .params(rosterId(n), position)
                    .update();
        }

        void updateUnavailable(int position, String assignments) {
            jdbc.sql("UPDATE fleet.unavailable_vehicle SET " + assignments
                            + " WHERE day_roster_id = ? AND position = ?")
                    .params(rosterId(n), position)
                    .update();
        }

        void insertASecondRoster() {
            jdbc.sql("INSERT INTO fleet.day_roster (id, version, date) SELECT ?, 0, date FROM fleet.day_roster"
                            + " WHERE id = ?")
                    .params(rosterId(100 + n), rosterId(n))
                    .update();
        }
    }

    // --- states, reached through the domain's commands

    /** Dana drives V1 and Omer V2, and V1 and V2 are marked unavailable, in that order. */
    private static DayRoster twoDriversAndTwoUnavailableVehicles(int n, DeliveryDate date) {
        DayRoster roster = DayRoster.open(rosterId(n), date);
        roster.fill(List.of(working(DANA, V1), working(OMER, V2)), ALL_ACTIVE);
        roster.markVehicleUnavailable(V1, Standing.ACTIVE);
        roster.markVehicleUnavailable(V2, Standing.ACTIVE);
        return roster;
    }

    private static RosterDefault working(AccountId driver, VehicleId vehicle) {
        return RosterDefault.working(driver, DAY, vehicle, Standing.ACTIVE);
    }

    // --- keys, dates and stored values

    private static UUID rosterId(int n) {
        return new UUID(KEY_PREFIX, n);
    }

    private static AccountId driver(int n) {
        return AccountId.of(new UUID(KEY_PREFIX + 1, n));
    }

    private static VehicleId vehicle(int n) {
        return VehicleId.of(new UUID(KEY_PREFIX + 2, n));
    }

    /** Test {@code n}'s own date: two days apart, so the day after it is the test's own too. */
    private static DeliveryDate dayOf(int n) {
        return DeliveryDate.of(FIRST_DAY.plusDays(2L * n));
    }

    private static DeliveryDate dayAfter(DeliveryDate date) {
        return DeliveryDate.of(date.value().plusDays(1));
    }

    private long storedVersion(int n) {
        return jdbc.sql("SELECT version FROM fleet.day_roster WHERE id = ?")
                .param(rosterId(n))
                .query(Long.class)
                .single();
    }
}
