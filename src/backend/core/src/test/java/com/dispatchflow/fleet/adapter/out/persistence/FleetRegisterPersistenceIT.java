package com.dispatchflow.fleet.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.PlanUses;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.fleet.domain.register.Capacity;
import com.dispatchflow.fleet.domain.register.Depot;
import com.dispatchflow.fleet.domain.register.DepotDescription;
import com.dispatchflow.fleet.domain.register.DepotHours;
import com.dispatchflow.fleet.domain.register.DepotName;
import com.dispatchflow.fleet.domain.register.FleetRegister;
import com.dispatchflow.fleet.domain.register.Plate;
import com.dispatchflow.fleet.domain.register.RegisterDescription;
import com.dispatchflow.fleet.domain.register.VehicleDetails;
import com.dispatchflow.fleet.domain.register.VehicleName;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import com.dispatchflow.shared.application.StaleVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * {@link FleetRegisterRepository} against a real PostgreSQL (ADR-126, ADR-127): what the company's
 * one register stores and loads as, with its depots and vehicles in the order they were added, its
 * version, the first save another call wins, the stored states the load check and the schema
 * refuse, and the check that keeps it to one row.
 *
 * <p>There is one register for the whole database, and only this class writes it, so each test
 * starts with none.
 */
@IntegrationTest
class FleetRegisterPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_7265_6769L;
    private static final Instant NOW = Instant.parse("2030-06-11T07:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2030, 6, 11);
    private static final DepotId NORTH = depot(1);
    private static final DepotId SOUTH = depot(2);
    private static final DepotId EAST = depot(3);
    private static final GeoPoint NORTH_POINT = GeoPoint.of(32.79, 35.0);
    private static final DepotHours DAY_HOURS = DepotHours.of(LocalTime.of(6, 0), LocalTime.of(20, 0));
    private static final Capacity VAN =
            Capacity.of(Weight.kilograms(new BigDecimal("812.5")), Volume.litres(new BigDecimal("6000.25")));

    @Autowired
    private FleetRegisterRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void startWithNoRegister() {
        jdbc.sql("DELETE FROM fleet.vehicle").update();
        jdbc.sql("DELETE FROM fleet.depot").update();
        jdbc.sql("DELETE FROM fleet.fleet_register").update();
    }

    @Test
    void GivenNoDepotOrVehicleWasEverAdded_WhenTheRegisterIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get())).isEmpty();
    }

    // --- round trips

    /** The states the round trip covers, reached through commands. */
    enum Scenario {
        NO_DEPOTS,
        TWO_DEPOTS,
        FULL,
        FULL_AND_A_RETIRED_VEHICLE,
        A_RETIRED_DEPOT_AND_ITS_RETIRED_VEHICLE,
        A_DEPOT_WITHOUT_A_POINT_OR_HOURS
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenARegister_WhenAddedAndLoadedInAnotherTransaction_ThenEveryDepotAndVehicleComesBackInOrder(
            Scenario scenario) {
        FleetRegister added = transactions.inTransaction(() -> {
            FleetRegister register = switch (scenario) {
                case NO_DEPOTS -> FleetRegister.empty();
                case TWO_DEPOTS -> twoDepots();
                case FULL -> withActiveVehicles(FleetRegister.MAX_ACTIVE_VEHICLES);
                case FULL_AND_A_RETIRED_VEHICLE -> withActiveVehicles(FleetRegister.MAX_ACTIVE_VEHICLES + 1);
                case A_RETIRED_DEPOT_AND_ITS_RETIRED_VEHICLE -> {
                    FleetRegister withEast = twoDepots();
                    withEast.addDepot(EAST, DepotName.of("East"), Optional.empty(), Optional.empty());
                    withEast.addVehicle(vehicle(1), details(1, EAST, EAST));
                    withEast.retireVehicle(vehicle(1), PlanUses.none(), TODAY, NOW);
                    withEast.retireDepot(EAST, PlanUses.none(), TODAY, NOW);
                    yield withEast;
                }
                case A_DEPOT_WITHOUT_A_POINT_OR_HOURS -> {
                    FleetRegister bare = FleetRegister.empty();
                    bare.addDepot(NORTH, DepotName.of("North"), Optional.empty(), Optional.empty());
                    yield bare;
                }
            };
            repository.add(register);
            return register;
        });

        // Read outside the transaction, so a collection the load left out would fail here.
        FleetRegister loaded = transactions.inTransaction(() -> repository.get().orElseThrow());

        assertThat(RegisterDescription.of(loaded)).isEqualTo(RegisterDescription.of(added));
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.activeVehicles()).isEqualTo(added.activeVehicles());
        assertThat(loaded.pendingEvents()).isEmpty();
        assertThat(jdbc.sql("SELECT id FROM fleet.fleet_register")
                        .query(UUID.class)
                        .single())
                .isEqualTo(FleetRegister.KEY);
    }

    @Test
    void GivenDepotsAddedInAnOrderTheirKeysDontSortIn_WhenLoaded_ThenTheyComeBackInTheOrderAdded() {
        transactions.inTransaction(() -> {
            FleetRegister register = FleetRegister.empty();
            register.addDepot(EAST, DepotName.of("East"), Optional.empty(), Optional.empty());
            register.addDepot(NORTH, DepotName.of("North"), Optional.empty(), Optional.empty());
            register.addDepot(SOUTH, DepotName.of("South"), Optional.empty(), Optional.empty());
            repository.add(register);
        });

        FleetRegister loaded = transactions.inTransaction(() -> repository.get().orElseThrow());

        assertThat(loaded.depots()).extracting(Depot::id).containsExactly(EAST, NORTH, SOUTH);
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedRegister_WhenADepotIsAddedAndThenRenamed_ThenTheVersionIsZeroThenOneThenTwo() {
        transactions.inTransaction(() -> repository.add(twoDepots()));
        long added = storedVersion();

        transactions.inTransaction(() -> {
            FleetRegister register = repository.get().orElseThrow();
            register.addDepot(EAST, DepotName.of("East"), Optional.empty(), Optional.empty());
            repository.save(register);
        });
        long withEast = storedVersion();
        // Only the depot's own row changes, so the save forces the register's version up.
        transactions.inTransaction(() -> {
            FleetRegister register = repository.get().orElseThrow();
            register.changeDepot(EAST, DepotName.of("East Gate"), Optional.of(NORTH_POINT));
            repository.save(register);
        });

        assertThat(List.of(added, withEast, storedVersion())).containsExactly(0L, 1L, 2L);
        assertThat(transactions.inTransaction(() -> DepotDescription.of(
                        repository.get().orElseThrow().depots().get(2))))
                .isEqualTo(new DepotDescription(
                        EAST, DepotName.of("East Gate"), Optional.of(NORTH_POINT), Optional.empty(), Optional.empty()));
    }

    @Test
    void GivenTwoDispatchersChangingTheRegister_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        transactions.inTransaction(() -> repository.add(twoDepots()));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    FleetRegister mine = repository.get().orElseThrow();
                    transactions.inANewTransaction(() -> {
                        FleetRegister theirs = repository.get().orElseThrow();
                        theirs.addVehicle(vehicle(1), details(1, NORTH, SOUTH));
                        repository.save(theirs);
                    });
                    mine.addVehicle(vehicle(2), details(2, NORTH, NORTH));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("FleetRegister");
                    assertThat(stale.id()).isEqualTo(FleetRegister.KEY);
                });

        FleetRegister stored = transactions.inTransaction(() -> repository.get().orElseThrow());
        assertThat(stored.version()).isEqualTo(Version.of(1));
        assertThat(stored.activeVehicles()).containsExactly(vehicle(1));
    }

    @Test
    void GivenAnotherCallStoredTheFirstRegister_WhenItIsAdded_ThenStaleVersionAndTheFirstStands() {
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    FleetRegister mine = twoDepots();
                    transactions.inANewTransaction(() -> {
                        FleetRegister theirs = FleetRegister.empty();
                        theirs.addDepot(EAST, DepotName.of("East"), Optional.empty(), Optional.empty());
                        repository.add(theirs);
                    });
                    repository.add(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("FleetRegister");
                    assertThat(stale.id()).isEqualTo(FleetRegister.KEY);
                });

        assertThat(transactions.inTransaction(
                        () -> repository.get().orElseThrow().depots()))
                .extracting(Depot::id)
                .containsExactly(EAST);
    }

    // --- the load check (ADR-127 decision 1): the damaged stored states it refuses

    static Stream<Arguments> damagedRows() {
        return Stream.of(
                Arguments.of("more active vehicles than the limit", Base.FULL, (Damage)
                        rows -> rows.copyVehicle(1, FleetRegister.MAX_ACTIVE_VEHICLES + 1)),
                Arguments.of("two active vehicles share a name", Base.TWO_ACTIVE_VEHICLES, (Damage)
                        rows -> rows.updateVehicle(2, "name = 'Vehicle 1'")),
                Arguments.of("two active vehicles share a name", Base.TWO_ACTIVE_VEHICLES, (Damage)
                        rows -> rows.updateVehicle(2, "name = 'Vehicle' || chr(160) || '1'")),
                Arguments.of("two active vehicles share a plate", Base.TWO_ACTIVE_VEHICLES, (Damage)
                        rows -> rows.updateVehicle(2, "plate = 'P1'")),
                Arguments.of("a vehicle uses a depot that is not in the register", Base.A_RETIRED_VEHICLE, (Damage)
                        rows -> rows.updateVehicle(2, "start_depot = '" + depot(9).value() + "'")),
                Arguments.of("an active vehicle uses a retired depot", Base.A_RETIRED_DEPOT, (Damage)
                        rows -> rows.updateVehicle(2, "end_depot = '" + EAST.value() + "'")),
                Arguments.of(StoredLists.GAP, Base.TWO_ACTIVE_VEHICLES, (Damage) Rows::shiftDepotPositions),
                Arguments.of(StoredLists.GAP, Base.TWO_ACTIVE_VEHICLES, (Damage) Rows::shiftVehiclePositions));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredRegisterBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String detail, Base base, Damage damage) {
        transactions.inTransaction(() -> repository.add(base.build()));

        damage.apply(new Rows(jdbc));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get()))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("FleetRegister");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    /**
     * No {@code CHECK} keeps a depot's hours in order, so only the registered instantiator stands
     * between the row and a {@code RuleViolation} that would read as the caller's mistake.
     */
    @Test
    void GivenAStoredDepotWhoseHoursCloseBeforeTheyOpen_WhenTheRegisterIsLoaded_ThenStoredStateInvalidNamesTheHours() {
        transactions.inTransaction(() -> repository.add(Base.TWO_ACTIVE_VEHICLES.build()));
        new Rows(jdbc).updateDepot(NORTH, "opens = '20:00', closes = '06:00'");

        Throwable thrown = catchThrowable(() -> transactions.inTransaction(() -> repository.get()));

        assertThat(Stream.iterate(thrown, Objects::nonNull, Throwable::getCause))
                .anySatisfy(cause -> assertThat(cause).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("DepotHours");
                    assertThat(invalid.detail()).isEqualTo("a stored value is refused with INVALID_DEPOT_HOURS");
                }));
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                // The domain's "a depot appears twice" and "a vehicle appears twice": each row's key
                // is the child's own ID.
                Arguments.of("a depot appears twice", (Damage) rows -> rows.copyDepotUnderItsOwnId(NORTH)),
                Arguments.of("a vehicle appears twice", (Damage) rows -> rows.copyVehicleUnderItsOwnId(1)),
                Arguments.of(
                        "a point without its longitude", (Damage) rows -> rows.updateDepot(NORTH, "longitude = NULL")),
                Arguments.of(
                        "hours without their closing time", (Damage) rows -> rows.updateDepot(NORTH, "closes = NULL")),
                Arguments.of("two depots in one place in the list", (Damage)
                        rows -> rows.updateDepot(SOUTH, "position = 0")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredRegister_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, Damage damage) {
        transactions.inTransaction(() -> repository.add(Base.TWO_ACTIVE_VEHICLES.build()));
        Rows rows = new Rows(jdbc);

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get())).isPresent();
    }

    @Test
    void GivenAnotherKey_WhenARowIsInsertedUnderIt_ThenTheDatabaseRefusesASecondRegister() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO fleet.fleet_register (id, version) VALUES (?, 0)")
                        .param(UUID.fromString("0190a5e2-636f-7000-8000-000000000001"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fleet_register_one_row");
    }

    /** The valid registers the damage cases start from, each with the North and South depots. */
    enum Base {
        FULL,
        TWO_ACTIVE_VEHICLES,
        A_RETIRED_VEHICLE,
        A_RETIRED_DEPOT;

        FleetRegister build() {
            return switch (this) {
                case FULL -> withActiveVehicles(FleetRegister.MAX_ACTIVE_VEHICLES);
                case TWO_ACTIVE_VEHICLES -> withActiveVehicles(2);
                case A_RETIRED_VEHICLE -> {
                    FleetRegister register = withActiveVehicles(2);
                    register.retireVehicle(vehicle(2), PlanUses.none(), TODAY, NOW);
                    yield register;
                }
                case A_RETIRED_DEPOT -> {
                    FleetRegister register = withActiveVehicles(2);
                    register.addDepot(EAST, DepotName.of("East"), Optional.empty(), Optional.empty());
                    register.retireDepot(EAST, PlanUses.none(), TODAY, NOW);
                    yield register;
                }
            };
        }
    }

    /** One damage done with SQL to the stored register. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run; every assignment is the test's own constant SQL. */
    record Rows(JdbcClient jdbc) {

        void updateVehicle(int n, String assignments) {
            jdbc.sql("UPDATE fleet.vehicle SET " + assignments + " WHERE id = ?")
                    .param(vehicle(n).value())
                    .update();
        }

        void updateDepot(DepotId depot, String assignments) {
            jdbc.sql("UPDATE fleet.depot SET " + assignments + " WHERE id = ?")
                    .param(depot.value())
                    .update();
        }

        /** Moves every depot one position on, so no depot holds the first. */
        void shiftDepotPositions() {
            jdbc.sql("UPDATE fleet.depot SET position = position + 1").update();
        }

        /** Moves every vehicle one position on, so no vehicle holds the first. */
        void shiftVehiclePositions() {
            jdbc.sql("UPDATE fleet.vehicle SET position = position + 1").update();
        }

        /** Adds vehicle {@code n} at the end, a copy of vehicle {@code from} under its own name and plate. */
        void copyVehicle(int from, int n) {
            jdbc.sql("""
                            INSERT INTO fleet.vehicle
                                (id, fleet_register_id, position, name, plate, weight_kg, volume_l, start_depot,
                                 end_depot, retired_at)
                            SELECT ?, fleet_register_id, (SELECT count(*) FROM fleet.vehicle), ?, ?, weight_kg,
                                   volume_l, start_depot, end_depot, retired_at
                            FROM fleet.vehicle WHERE id = ?
                            """)
                    .params(
                            vehicle(n).value(),
                            "Vehicle " + n,
                            "P" + n,
                            vehicle(from).value())
                    .update();
        }

        void copyVehicleUnderItsOwnId(int n) {
            jdbc.sql("""
                            INSERT INTO fleet.vehicle
                                (id, fleet_register_id, position, name, plate, weight_kg, volume_l, start_depot,
                                 end_depot, retired_at)
                            SELECT id, fleet_register_id, (SELECT count(*) FROM fleet.vehicle), name, plate,
                                   weight_kg, volume_l, start_depot, end_depot, now()
                            FROM fleet.vehicle WHERE id = ?
                            """).param(vehicle(n).value()).update();
        }

        void copyDepotUnderItsOwnId(DepotId depot) {
            jdbc.sql("""
                            INSERT INTO fleet.depot (id, fleet_register_id, position, name)
                            SELECT id, fleet_register_id, (SELECT count(*) FROM fleet.depot), 'Again'
                            FROM fleet.depot WHERE id = ?
                            """).param(depot.value()).update();
        }
    }

    // --- states, reached through the domain's commands

    /** North, with a point and hours, and South, with neither. */
    private static FleetRegister twoDepots() {
        FleetRegister register = FleetRegister.empty();
        register.addDepot(NORTH, DepotName.of("North"), Optional.of(NORTH_POINT), Optional.of(DAY_HOURS));
        register.addDepot(SOUTH, DepotName.of("South"), Optional.empty(), Optional.empty());
        return register;
    }

    /**
     * {@link #twoDepots()} and {@code count} active vehicles, numbered from 1 and each with its own
     * name and plate, retiring the earliest ones once the register is full.
     */
    private static FleetRegister withActiveVehicles(int count) {
        FleetRegister register = twoDepots();
        for (int n = 1; n <= count; n++) {
            if (n > FleetRegister.MAX_ACTIVE_VEHICLES) {
                register.retireVehicle(vehicle(n - FleetRegister.MAX_ACTIVE_VEHICLES), PlanUses.none(), TODAY, NOW);
            }
            register.addVehicle(vehicle(n), details(n, NORTH, n % 2 == 0 ? SOUTH : NORTH));
        }
        assertThat(register.standingOf(vehicle(count))).isEqualTo(Standing.ACTIVE);
        return register;
    }

    private static VehicleDetails details(int n, DepotId start, DepotId end) {
        return new VehicleDetails(VehicleName.of("Vehicle " + n), Plate.of("P" + n), VAN, start, end);
    }

    // --- keys and stored values

    private static DepotId depot(int n) {
        return DepotId.of(new UUID(KEY_PREFIX, n));
    }

    private static VehicleId vehicle(int n) {
        return VehicleId.of(new UUID(KEY_PREFIX + 1, n));
    }

    private long storedVersion() {
        return jdbc.sql("SELECT version FROM fleet.fleet_register")
                .query(Long.class)
                .single();
    }
}
