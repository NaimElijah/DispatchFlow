package com.dispatchflow.planning.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.dispatchflow.DatabaseOwner;
import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.settings.Buffer;
import com.dispatchflow.planning.domain.settings.DepartureAllowance;
import com.dispatchflow.planning.domain.settings.FixedVehicleCost;
import com.dispatchflow.planning.domain.settings.PlanningSettings;
import com.dispatchflow.planning.domain.settings.PlanningSettingsDescription;
import com.dispatchflow.planning.domain.settings.TravelBucket;
import com.dispatchflow.planning.domain.settings.TravelBuckets;
import com.dispatchflow.planning.domain.settings.TravelFactor;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link PlanningSettingsRepository} against a real PostgreSQL (ADR-126, ADR-127): the starting
 * values before an Admin saves any, what the settings store and load as under their constant key,
 * the version, and the stored values the load refuses. The settings are one row, so each test starts
 * with none stored.
 */
@IntegrationTest
class PlanningSettingsPersistenceIT {

    private static final TravelBuckets CHANGED_BUCKETS = new TravelBuckets(
            new TravelBucket(LocalTime.of(6, 30), LocalTime.of(9, 0), TravelFactor.of(new BigDecimal("1.35"))),
            new TravelBucket(LocalTime.of(9, 0), LocalTime.of(10, 15), TravelFactor.of(new BigDecimal("1.1"))),
            TravelFactor.of(new BigDecimal("0.95")));

    @Autowired
    private PlanningSettingsRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    @DatabaseOwner
    private JdbcClient owner;

    @BeforeEach
    void startWithNoSettings() {
        owner.sql("DELETE FROM planning.planning_settings").update();
    }

    @Test
    void GivenNoAdminChangedTheSettings_WhenTheCurrentOnesAreRead_ThenTheyAreTheStartingValuesNeverSaved() {
        PlanningSettings current = transactions.inTransaction(() -> repository.current());

        assertThat(PlanningSettingsDescription.of(current))
                .isEqualTo(PlanningSettingsDescription.of(PlanningSettings.startingValues()));
        assertThat(current.savedVersion()).isEmpty();
    }

    @Test
    void GivenEverySettingChanged_WhenSavedAndLoadedInAnotherTransaction_ThenEveryValueComesBackUnderTheConstantKey() {
        PlanningSettingsDescription saved = transactions.inTransaction(() -> {
            PlanningSettings settings = repository.current();
            changeEverySetting(settings);
            PlanningSettingsDescription before = PlanningSettingsDescription.of(settings);
            repository.save(settings);
            return before;
        });

        PlanningSettings loaded = transactions.inTransaction(() -> repository.current());

        assertThat(PlanningSettingsDescription.of(loaded)).isEqualTo(saved.at(Version.of(0)));
        assertThat(loaded.pendingEvents()).isEmpty();
        assertThat(jdbc.sql("SELECT id FROM planning.planning_settings")
                        .query(UUID.class)
                        .single())
                .isEqualTo(PlanningSettings.KEY);
    }

    @Test
    void GivenTheStartingValuesSavedUnchanged_WhenLoaded_ThenTheyComeBackWithNoFixedVehicleCost() {
        transactions.inTransaction(() -> repository.save(repository.current()));

        assertThat(PlanningSettingsDescription.of(transactions.inTransaction(() -> repository.current())))
                .isEqualTo(PlanningSettingsDescription.of(PlanningSettings.startingValues())
                        .at(Version.of(0)));
    }

    @Test
    void GivenSettingsNeverSaved_WhenSavedThenChangedAndSavedTwice_ThenTheVersionIsZeroThenOneThenTwo() {
        transactions.inTransaction(() -> repository.save(repository.current()));
        long first = storedVersion();
        transactions.inTransaction(() -> {
            PlanningSettings settings = repository.current();
            settings.changeFixedVehicleCost(new FixedVehicleCost.On(Duration.ofMinutes(20)));
            repository.save(settings);
        });
        long changed = storedVersion();
        transactions.inTransaction(() -> {
            PlanningSettings settings = repository.current();
            settings.changeFixedVehicleCost(new FixedVehicleCost.Off());
            repository.save(settings);
        });

        assertThat(List.of(first, changed, storedVersion())).containsExactly(0L, 1L, 2L);
        assertThat(jdbc.sql("SELECT fixed_vehicle_cost_per_vehicle FROM planning.planning_settings")
                        .query(Long.class)
                        .optional())
                .isEmpty();
    }

    @Test
    void GivenTwoAdminsChangingTheSettings_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        transactions.inTransaction(() -> repository.save(repository.current()));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    PlanningSettings mine = repository.current();
                    transactions.inANewTransaction(() -> {
                        PlanningSettings theirs = repository.current();
                        theirs.changeBuffer(Buffer.percent(new BigDecimal("12.5")));
                        repository.save(theirs);
                    });
                    mine.changeDepartureAllowance(DepartureAllowance.ofMinutes(10));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("PlanningSettings");
                    assertThat(stale.id()).isEqualTo(PlanningSettings.KEY);
                });

        PlanningSettingsDescription stored =
                transactions.inTransaction(() -> PlanningSettingsDescription.of(repository.current()));
        assertThat(stored.version()).contains(Version.of(1));
        assertThat(stored.buffer()).isEqualTo(Buffer.percent(new BigDecimal("12.5")));
        assertThat(stored.departureAllowance()).isEqualTo(DepartureAllowance.STARTING);
    }

    @Test
    void GivenAnotherAdminSavedTheFirstSettings_WhenTheyAreSavedFirst_ThenStaleVersionAndTheFirstStand() {
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    PlanningSettings mine = repository.current();
                    transactions.inANewTransaction(() -> {
                        PlanningSettings theirs = repository.current();
                        theirs.changeDefaultSolveLimit(SolveLimit.daily(Duration.ofSeconds(30)));
                        repository.save(theirs);
                    });
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("PlanningSettings");
                    assertThat(stale.id()).isEqualTo(PlanningSettings.KEY);
                });

        assertThat(transactions.inTransaction(() -> repository.current().defaultSolveLimit()))
                .isEqualTo(SolveLimit.daily(Duration.ofSeconds(30)));
    }

    // --- the load check: a stored value its type refuses (ADR-126, ADR-127 decision 1)

    static Stream<Arguments> refusedValues() {
        String refused = "a stored value is refused with ";
        return Stream.of(
                Arguments.of(
                        "a morning peak that ends before it starts",
                        "morning_peak_end = '07:00'",
                        "TravelBucket",
                        refused + "INVALID_TRAVEL_BUCKETS"),
                Arguments.of(
                        "an evening peak that starts in the morning peak",
                        "evening_peak_start = '08:00'",
                        "TravelBuckets",
                        refused + "INVALID_TRAVEL_BUCKETS"),
                Arguments.of(
                        "an enabled fixed vehicle cost of zero",
                        "fixed_vehicle_cost = 'ON', fixed_vehicle_cost_per_vehicle = 0",
                        "FixedVehicleCost",
                        refused + "NON_POSITIVE_FIXED_VEHICLE_COST"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedValues")
    void GivenAStoredValueItsTypeRefuses_WhenTheSettingsAreLoaded_ThenStoredStateInvalid(
            String broken, String assignments, String subject, String detail) {
        storeStartingValuesWith(assignments);

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.current()))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of("a negative buffer", "buffer_percent = -1", "Buffer"),
                Arguments.of("a travel factor of zero", "off_peak_factor = 0", "TravelFactor"),
                Arguments.of("a default limit of 61 seconds", "default_solve_limit = 61000000000", "SolveLimit"),
                Arguments.of(
                        "a negative departure allowance", "departure_allowance_minutes = -1", "DepartureAllowance"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedColumns")
    void GivenAStoredColumnItsValueRefuses_WhenTheSettingsAreLoaded_ThenItsConverterRaisesStoredStateInvalid(
            String broken, String assignments, String subject) {
        storeStartingValuesWith(assignments);

        // Hibernate wraps a converter's exception, and Spring wraps Hibernate's.
        Throwable thrown = catchThrowable(() -> transactions.inTransaction(() -> repository.current()));
        assertThat(Stream.iterate(thrown, cause -> cause != null, Throwable::getCause))
                .anyMatch(cause -> cause instanceof StoredStateInvalid invalid
                        && invalid.subject().equals(subject));
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("a second row under another key", "id = gen_random_uuid()"),
                Arguments.of("a fixed vehicle cost no setting has", "fixed_vehicle_cost = 'SOMETIMES'"),
                Arguments.of("an enabled fixed vehicle cost without its duration", "fixed_vehicle_cost = 'ON'"),
                Arguments.of("a fixed vehicle cost that is off with a duration", "fixed_vehicle_cost_per_vehicle = 60"),
                Arguments.of("a peak without its factor", "evening_peak_factor = NULL"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenStoredSettings_WhenTheirRowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, String assignments) {
        transactions.inTransaction(() -> repository.save(repository.current()));

        assertThatThrownBy(() -> owner.sql("UPDATE planning.planning_settings SET " + assignments)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.current()).savedVersion())
                .isEqualTo(Optional.of(Version.of(0)));
    }

    // --- helpers

    private static void changeEverySetting(PlanningSettings settings) {
        settings.changeTravelBuckets(CHANGED_BUCKETS);
        settings.changeBuffer(Buffer.percent(new BigDecimal("7.25")));
        settings.changeDefaultSolveLimit(SolveLimit.daily(Duration.ofSeconds(60)));
        settings.changeFixedVehicleCost(
                new FixedVehicleCost.On(Duration.ofMinutes(30).plusNanos(1)));
        settings.changeDepartureAllowance(DepartureAllowance.ofMinutes(15));
    }

    /** Saves the starting values, then changes their row with SQL: the test's own constant assignments. */
    private void storeStartingValuesWith(String assignments) {
        transactions.inTransaction(() -> repository.save(repository.current()));
        owner.sql("UPDATE planning.planning_settings SET " + assignments).update();
    }

    private long storedVersion() {
        return jdbc.sql("SELECT version FROM planning.planning_settings")
                .query(Long.class)
                .single();
    }
}
