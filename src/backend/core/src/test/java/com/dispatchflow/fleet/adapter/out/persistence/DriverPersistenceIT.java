package com.dispatchflow.fleet.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.domain.PlanUses;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.fleet.domain.driver.Driver;
import com.dispatchflow.fleet.domain.driver.DriverContact;
import com.dispatchflow.fleet.domain.driver.DriverDescription;
import com.dispatchflow.fleet.domain.driver.DriverName;
import com.dispatchflow.fleet.domain.driver.DriverStatus;
import com.dispatchflow.fleet.domain.driver.WeeklyShifts;
import com.dispatchflow.fleet.events.DriverRetired;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link DriverRepository} against a real PostgreSQL (ADR-126, ADR-127): what a driver stores and
 * loads as, the version and its events, the registration order, the drivers due for anonymization,
 * and the stored states the load check and the schema refuse.
 *
 * <p>The database is shared and never cleaned, and two of the port's lookups read every driver. So
 * test {@code n} keys its own drivers ({@code account(n)}, {@code account(100 + n)}, …) and reads
 * only its own drivers out of a lookup's answer.
 */
@IntegrationTest
class DriverPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6472_6976L;
    private static final Instant NOW = Instant.parse("2030-06-11T07:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2030, 6, 11);
    private static final ShiftHours DAY = ShiftHours.of(LocalTime.of(8, 0), LocalTime.of(16, 0));
    private static final ShiftHours MORNING = ShiftHours.of(LocalTime.of(6, 0), LocalTime.of(14, 0));
    private static final WeeklyShifts SUNDAY_AND_FRIDAY =
            WeeklyShifts.of(Map.of(DayOfWeek.SUNDAY, MORNING, DayOfWeek.FRIDAY, DAY));
    private static final DeliveryDate A_SUNDAY = DeliveryDate.of(LocalDate.of(2030, 6, 9));

    @Autowired
    private DriverRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedEvents events;

    @BeforeEach
    void forgetEarlierEvents() {
        events.clear();
    }

    // --- round trips

    /** The states the round trip covers, reached through commands. */
    enum Scenario {
        ACTIVE,
        WITH_WEEKLY_SHIFTS,
        RETIRED,
        ANONYMIZED,
        ANONYMIZED_WITH_THEIR_ACCOUNT
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenADriver_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        Driver added = transactions.inTransaction(() -> {
            Driver driver = switch (scenario) {
                case ACTIVE -> active(n);
                case WITH_WEEKLY_SHIFTS -> withWeeklyShifts(n);
                case RETIRED -> retired(n, NOW);
                case ANONYMIZED -> anonymized(n);
                case ANONYMIZED_WITH_THEIR_ACCOUNT -> anonymizedWithTheirAccount(n);
            };
            repository.add(driver);
            return driver;
        });

        Driver loaded =
                transactions.inTransaction(() -> repository.get(account(n)).orElseThrow());

        assertThat(DriverDescription.of(loaded)).isEqualTo(DriverDescription.of(added));
        assertThat(loaded.id()).isEqualTo(account(n));
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.rosterDefaultOn(A_SUNDAY, Standing.ACTIVE))
                .isEqualTo(added.rosterDefaultOn(A_SUNDAY, Standing.ACTIVE));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAnAccountWithNoDriverRecord_WhenItsDriverIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(account(6)))).isEmpty();
    }

    // --- versions, events and conflicts

    @Test
    void GivenAnAddedDriver_WhenTheirDetailsAndThenTheirWeeklyShiftsChange_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 7;
        transactions.inTransaction(() -> repository.add(active(n)));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            Driver driver = repository.get(account(n)).orElseThrow();
            driver.changeDetails(contact(100 + n), MORNING, vehicle(100 + n));
            repository.save(driver);
        });
        long changed = storedVersion(n);
        transactions.inTransaction(() -> {
            Driver driver = repository.get(account(n)).orElseThrow();
            driver.changeWeeklyShifts(SUNDAY_AND_FRIDAY);
            repository.save(driver);
        });

        assertThat(List.of(added, changed, storedVersion(n))).containsExactly(0L, 1L, 2L);
        assertThat(transactions.inTransaction(
                        () -> DriverDescription.of(repository.get(account(n)).orElseThrow())))
                .isEqualTo(new DriverDescription(
                        account(n),
                        new DriverStatus.Active(),
                        Optional.of(contact(100 + n)),
                        MORNING,
                        vehicle(100 + n),
                        SUNDAY_AND_FRIDAY));
    }

    @Test
    void GivenAStoredDriver_WhenTheyAreRetiredAndSaved_ThenDriverRetiredIsPublishedAtTheVersionSaved() {
        int n = 8;
        transactions.inTransaction(() -> repository.add(active(n)));

        transactions.inTransaction(() -> {
            Driver driver = repository.get(account(n)).orElseThrow();
            driver.retire(PlanUses.none(), TODAY, NOW);
            repository.save(driver);
        });

        assertThat(events.committed())
                .containsExactly(new DriverRetired(
                        new EventId("DriverRetired", account(n).value(), Version.of(1), 0), NOW, account(n)));
        assertThat(storedVersion(n)).isEqualTo(1L);
    }

    @Test
    void GivenTwoTransactionsOnOneDriver_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 9;
        transactions.inTransaction(() -> repository.add(active(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Driver mine = repository.get(account(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Driver theirs = repository.get(account(n)).orElseThrow();
                        theirs.changeDetails(contact(100 + n), MORNING, vehicle(n));
                        repository.save(theirs);
                    });
                    mine.changeWeeklyShifts(SUNDAY_AND_FRIDAY);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Driver");
                    assertThat(stale.id()).isEqualTo(account(n).value());
                });

        Driver stored =
                transactions.inTransaction(() -> repository.get(account(n)).orElseThrow());
        assertThat(stored.version()).isEqualTo(Version.of(1));
        assertThat(stored.defaultShift()).isEqualTo(MORNING);
        assertThat(stored.weeklyShifts()).isEqualTo(WeeklyShifts.none());
    }

    /** A driver is keyed by their account, so two registrations of one account race for that key. */
    @Test
    void GivenAnotherCallRegisteredTheAccountsDriverFirst_WhenTheDriverIsAdded_ThenStaleVersionAndTheFirstStands() {
        int n = 10;

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Driver mine = active(n);
                    transactions.inANewTransaction(() -> repository.add(
                            Driver.register(account(n), contact(100 + n), MORNING, vehicle(n), Standing.ACTIVE)));
                    repository.add(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Driver");
                    assertThat(stale.id()).isEqualTo(account(n).value());
                });

        Driver stored =
                transactions.inTransaction(() -> repository.get(account(n)).orElseThrow());
        assertThat(stored.defaultShift()).isEqualTo(MORNING);
        assertThat(storedVersion(n)).isZero();
    }

    // --- every driver, in registration order

    @Test
    void GivenDriversRegisteredInEveryState_WhenAllAreRead_ThenEachComesWholeInTheOrderTheyWereRegistered() {
        // Registered in an order their keys don't sort in.
        int first = 21;
        int second = 20;
        int third = 22;
        transactions.inTransaction(() -> repository.add(withWeeklyShifts(first)));
        transactions.inTransaction(() -> repository.add(retired(second, NOW)));
        transactions.inTransaction(() -> repository.add(anonymized(third)));
        Set<AccountId> ours = Set.of(account(first), account(second), account(third));

        List<Driver> registered = transactions.inTransaction(() -> repository.registered());

        // Read outside the transaction, so a collection it didn't load would fail here.
        assertThat(registered.stream().map(DriverDescription::of).filter(driver -> ours.contains(driver.id())))
                .extracting(DriverDescription::id, DriverDescription::weeklyShifts)
                .containsExactly(
                        tuple(account(first), SUNDAY_AND_FRIDAY),
                        tuple(account(second), WeeklyShifts.none()),
                        tuple(account(third), WeeklyShifts.none()));
    }

    // --- the drivers due for anonymization (BR-98, NFR-184)

    @Test
    void GivenDriversInEveryState_WhenTheDueOnesAreListed_ThenOnlyThoseRetiredAnIsraelYearAgoOrMoreAreDue() {
        int due = 30;
        int active = 31;
        int anonymized = 32;
        int retiredADayLater = 33;
        // In 2025, before every other test's retirement. One Israel year from 27 March 2025, still
        // standard time, ends on 27 March 2026, already summer time: an hour before 365 days pass.
        Instant retiredAt = israel(LocalDateTime.of(2025, 3, 27, 12, 0));
        Instant end =
                retiredAt.atZone(IsraelDateTime.ZONE).plus(Driver.RETENTION).toInstant();
        transactions.inTransaction(() -> {
            repository.add(retired(due, retiredAt));
            repository.add(active(active));
            Driver gone = retired(anonymized, retiredAt);
            gone.anonymize(end);
            repository.add(gone);
            repository.add(retired(retiredADayLater, retiredAt.plus(Duration.ofDays(1))));
        });
        Set<AccountId> ours = Set.of(account(due), account(active), account(anonymized), account(retiredADayLater));

        List<AccountId> beforeTheEnd = transactions.inTransaction(() -> dueInAUtcSession(end.minusSeconds(1)));
        List<AccountId> atTheEnd = transactions.inTransaction(() -> dueInAUtcSession(end));
        List<AccountId> muchLater = transactions.inTransaction(() -> dueInAUtcSession(end.plus(Duration.ofDays(400))));

        assertThat(end).isEqualTo(retiredAt.plus(Duration.ofDays(365)).minus(Duration.ofHours(1)));
        assertThat(beforeTheEnd).filteredOn(ours::contains).isEmpty();
        assertThat(atTheEnd).filteredOn(ours::contains).containsExactly(account(due));
        assertThat(muchLater)
                .filteredOn(ours::contains)
                .containsExactlyInAnyOrder(account(due), account(retiredADayLater));
    }

    // --- the load check (ADR-127 decision 1): the damaged stored states it refuses

    private static final String CONTACT_UNTIL_ANONYMIZED =
            "a driver has a name and phone exactly until they are anonymized";

    static Stream<Arguments> damagedRows() {
        return Stream.of(
                Arguments.of(
                        "an active driver without a name and phone",
                        1_001,
                        Base.ACTIVE,
                        "name = NULL, phone = NULL",
                        CONTACT_UNTIL_ANONYMIZED),
                Arguments.of(
                        "a retired driver without a name and phone",
                        1_002,
                        Base.RETIRED,
                        "name = NULL, phone = NULL",
                        CONTACT_UNTIL_ANONYMIZED),
                Arguments.of(
                        "anonymized before being retired",
                        1_004,
                        Base.ANONYMIZED,
                        "anonymized_at = retired_at - interval '1 second'",
                        "a driver is anonymized only after being retired"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredDriverBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Base base, String damage, String detail) {
        transactions.inTransaction(() -> repository.add(base.build(n)));

        // The damage and the load share a transaction, which the refused load rolls back, so the
        // registered drivers other tests read never include a damaged row.
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    damage(n, damage);
                    return repository.get(account(n));
                }))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("Driver");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
        assertThat(transactions.inTransaction(() -> repository.get(account(n)))).isPresent();
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("a name without a phone", 1_005, Base.ACTIVE, "phone = NULL"),
                Arguments.of(
                        "an active driver with a retirement time",
                        1_006,
                        Base.ACTIVE,
                        "retired_at = TIMESTAMPTZ '2031-01-01 08:00:00+00'"),
                Arguments.of("a retired driver with no retirement time", 1_007, Base.RETIRED, "retired_at = NULL"),
                Arguments.of(
                        "an anonymized driver with no anonymization time",
                        1_008,
                        Base.ANONYMIZED,
                        "anonymized_at = NULL"),
                Arguments.of("an unknown status", 1_009, Base.ACTIVE, "status = 'SUSPENDED'"),
                Arguments.of(
                        "an anonymized driver who still has a name and phone",
                        1_003,
                        Base.ANONYMIZED,
                        "name = 'Dana Levi', phone = '+972523456789'"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredDriver_WhenTheRowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Base base, String damage) {
        transactions.inTransaction(() -> repository.add(base.build(n)));

        assertThatThrownBy(() -> damage(n, damage)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(account(n)))).isPresent();
    }

    /** The valid states the damage cases start from. */
    enum Base {
        ACTIVE,
        RETIRED,
        ANONYMIZED;

        Driver build(int n) {
            return switch (this) {
                case ACTIVE -> active(n);
                case RETIRED -> retired(n, NOW);
                case ANONYMIZED -> anonymized(n);
            };
        }
    }

    // --- states, reached through the domain's commands

    private static Driver active(int n) {
        return Driver.register(account(n), contact(n), DAY, vehicle(n), Standing.ACTIVE);
    }

    private static Driver withWeeklyShifts(int n) {
        Driver driver = active(n);
        driver.changeWeeklyShifts(SUNDAY_AND_FRIDAY);
        return driver;
    }

    private static Driver retired(int n, Instant at) {
        Driver driver = active(n);
        driver.retire(PlanUses.none(), TODAY, at);
        return driver;
    }

    /** Retired at {@link #NOW}, and anonymized when their retention ends. */
    private static Driver anonymized(int n) {
        Driver driver = retired(n, NOW);
        driver.anonymize(NOW.atZone(IsraelDateTime.ZONE).plus(Driver.RETENTION).toInstant());
        return driver;
    }

    private static Driver anonymizedWithTheirAccount(int n) {
        Driver driver = active(n);
        driver.anonymizeWithAccount(NOW);
        return driver;
    }

    // --- keys, values and stored rows

    private static AccountId account(int n) {
        return AccountId.of(new UUID(KEY_PREFIX, n));
    }

    private static VehicleId vehicle(int n) {
        return VehicleId.of(new UUID(KEY_PREFIX + 1, n));
    }

    /** A name and an Israeli mobile number of its own for each {@code n}. */
    private static DriverContact contact(int n) {
        return new DriverContact(
                DriverName.of("Driver " + n), PhoneNumber.ofStored("+97252%07d".formatted(4_000_000 + n)));
    }

    private static Instant israel(LocalDateTime local) {
        return local.atZone(IsraelDateTime.ZONE).toInstant();
    }

    /**
     * Lists the due drivers in a session whose zone is UTC, not the JVM's, so the answer shows the
     * SQL keeps Israel's calendar whatever the session's zone.
     */
    private List<AccountId> dueInAUtcSession(Instant now) {
        jdbc.sql("SET LOCAL TimeZone = 'UTC'").update();
        return repository.dueForAnonymization(now);
    }

    /** Sets columns of driver {@code n}'s row; the assignments are the test's own constant SQL. */
    private void damage(int n, String assignments) {
        jdbc.sql("UPDATE fleet.driver SET " + assignments + " WHERE id = ?")
                .param(account(n).value())
                .update();
    }

    private long storedVersion(int n) {
        return jdbc.sql("SELECT version FROM fleet.driver WHERE id = ?")
                .param(account(n).value())
                .query(Long.class)
                .single();
    }
}
