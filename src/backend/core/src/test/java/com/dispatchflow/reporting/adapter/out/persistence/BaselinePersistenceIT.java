package com.dispatchflow.reporting.adapter.out.persistence;

import static com.dispatchflow.reporting.domain.ReportingFixtures.BASELINE_FIGURES;
import static com.dispatchflow.reporting.domain.ReportingFixtures.BASELINE_WITH_ADDED_ORDER;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_C;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_D;
import static com.dispatchflow.reporting.domain.ReportingFixtures.RECOMPUTED_FIGURES;
import static com.dispatchflow.reporting.domain.ReportingFixtures.SHIFT_RAN_OUT_2;
import static com.dispatchflow.reporting.domain.ReportingFixtures.UNAVAILABLE_1;
import static com.dispatchflow.reporting.domain.ReportingFixtures.VEHICLE_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.reporting.application.port.out.RefusalCause;
import com.dispatchflow.reporting.domain.baseline.Baseline;
import com.dispatchflow.reporting.domain.baseline.BaselineCause;
import com.dispatchflow.reporting.domain.baseline.BaselineDescription;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * {@link BaselineRepository} against a real PostgreSQL (ADR-126, ADR-127): what a baseline stores
 * and loads as, the version, the refusal a plan may hold instead, the rule that a plan holds a
 * baseline or a refusal and never both, and the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, and no query here reads more than one plan, so test
 * {@code n} keys its own plan, {@code plan(n)}.
 */
@IntegrationTest
class BaselinePersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6261_7365L;
    private static final Instant PUBLISHED_AT = Instant.parse("2027-06-12T15:00:00Z");
    private static final Instant REVISED_AT = Instant.parse("2027-06-13T10:00:00Z");
    private static final Instant REVISED_AGAIN_AT = Instant.parse("2027-06-13T11:00:00Z");

    @Autowired
    private BaselineRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    // --- round trips (ADR-127 decision 1)

    /** The states a baseline stores and loads in, reached through its factory and commands. */
    enum Scenario {
        PUBLISHED,
        ONE_VEHICLE_WITHDRAWN,
        TWO_VEHICLES_WITHDRAWN,
        AN_ORDER_ADDED,
        WITHDRAWN_THEN_ADDED;

        Baseline build(PlanId plan) {
            Baseline baseline = Baseline.compute(plan, BASELINE_FIGURES, PUBLISHED_AT);
            switch (this) {
                case PUBLISHED -> {}
                case ONE_VEHICLE_WITHDRAWN ->
                    baseline.recompute(List.of(UNAVAILABLE_1), List.of(), RECOMPUTED_FIGURES, REVISED_AT);
                case TWO_VEHICLES_WITHDRAWN ->
                    baseline.recompute(
                            List.of(SHIFT_RAN_OUT_2, UNAVAILABLE_1), List.of(), RECOMPUTED_FIGURES, REVISED_AT);
                case AN_ORDER_ADDED ->
                    baseline.recompute(List.of(), List.of(ORDER_D), BASELINE_WITH_ADDED_ORDER, REVISED_AT);
                case WITHDRAWN_THEN_ADDED -> {
                    baseline.recompute(List.of(UNAVAILABLE_1), List.of(), RECOMPUTED_FIGURES, REVISED_AT);
                    baseline.recompute(List.of(), List.of(ORDER_D), BASELINE_WITH_ADDED_ORDER, REVISED_AGAIN_AT);
                }
            }
            return baseline;
        }
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenABaseline_WhenAddedAndLoadedInAnotherTransaction_ThenItsWholeStateComesBackAsAdded(Scenario scenario) {
        PlanId plan = plan(1 + scenario.ordinal());
        BaselineDescription added = transactions.inTransaction(() -> {
            Baseline baseline = scenario.build(plan);
            repository.add(baseline);
            return BaselineDescription.of(baseline);
        });

        Baseline loaded = transactions.inTransaction(() -> repository.get(plan).orElseThrow());

        // Read outside the transaction, so a collection the load left unfetched fails here.
        assertThat(BaselineDescription.of(loaded)).isEqualTo(added);
        assertThat(loaded.plan()).isEqualTo(plan);
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoBaselineOfAPlan_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(plan(9)))).isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedBaseline_WhenRecomputedAndSavedTwice_ThenTheVersionIsZeroThenOneThenTwoAndTheLatestStands() {
        PlanId plan = plan(10);
        transactions.inTransaction(() -> repository.add(Baseline.compute(plan, BASELINE_FIGURES, PUBLISHED_AT)));
        long added = storedVersion(plan);

        transactions.inTransaction(() -> {
            Baseline baseline = repository.get(plan).orElseThrow();
            baseline.recompute(List.of(), List.of(ORDER_D), BASELINE_WITH_ADDED_ORDER, REVISED_AT);
            repository.save(baseline);
        });
        long grown = storedVersion(plan);
        transactions.inTransaction(() -> {
            Baseline baseline = repository.get(plan).orElseThrow();
            baseline.recompute(List.of(UNAVAILABLE_1), List.of(ORDER_C), RECOMPUTED_FIGURES, REVISED_AGAIN_AT);
            repository.save(baseline);
        });

        assertThat(List.of(added, grown, storedVersion(plan))).containsExactly(0L, 1L, 2L);
        assertThat(transactions.inTransaction(
                        () -> BaselineDescription.of(repository.get(plan).orElseThrow())))
                .isEqualTo(new BaselineDescription(
                        plan,
                        Optional.of(Version.of(2)),
                        RECOMPUTED_FIGURES,
                        new BaselineCause.Recomputed(List.of(UNAVAILABLE_1), List.of(ORDER_D, ORDER_C)),
                        REVISED_AGAIN_AT));
    }

    @Test
    void GivenTwoTransactionsOnOneBaseline_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstRecomputeStands() {
        PlanId plan = plan(11);
        transactions.inTransaction(() -> repository.add(Baseline.compute(plan, BASELINE_FIGURES, PUBLISHED_AT)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Baseline mine = repository.get(plan).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Baseline theirs = repository.get(plan).orElseThrow();
                        theirs.recompute(List.of(UNAVAILABLE_1), List.of(), RECOMPUTED_FIGURES, REVISED_AT);
                        repository.save(theirs);
                    });
                    mine.recompute(List.of(), List.of(ORDER_D), BASELINE_WITH_ADDED_ORDER, REVISED_AT);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Baseline");
                    assertThat(stale.id()).isEqualTo(plan.value());
                });

        assertThat(storedVersion(plan)).isEqualTo(1L);
        assertThat(transactions.inTransaction(
                        () -> repository.get(plan).orElseThrow().cause()))
                .isEqualTo(new BaselineCause.Recomputed(List.of(UNAVAILABLE_1), List.of()));
    }

    @Test
    void GivenAnotherDeliveryStoredThePlansBaselineFirst_WhenItIsAdded_ThenStaleVersionAndTheFirstStands() {
        PlanId plan = plan(12);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Baseline mine = Baseline.compute(plan, BASELINE_FIGURES, PUBLISHED_AT);
                    transactions.inANewTransaction(
                            () -> repository.add(Baseline.compute(plan, RECOMPUTED_FIGURES, PUBLISHED_AT)));
                    repository.add(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Baseline");
                    assertThat(stale.id()).isEqualTo(plan.value());
                });

        assertThat(transactions.inTransaction(
                        () -> repository.get(plan).orElseThrow().result()))
                .isEqualTo(RECOMPUTED_FIGURES);
    }

    // --- a refusal instead of a baseline (FR-39)

    @ParameterizedTest
    @EnumSource(RefusalCause.class)
    void GivenARecordedRefusal_WhenItIsLoaded_ThenItsCauseComesBackAndThePlanHasNoBaseline(RefusalCause cause) {
        PlanId plan = plan(20 + cause.ordinal());

        transactions.inTransaction(() -> repository.recordRefusal(plan, cause));

        assertThat(transactions.inTransaction(() -> repository.refusalOf(plan))).contains(cause);
        assertThat(transactions.inTransaction(() -> repository.get(plan))).isEmpty();
    }

    @Test
    void GivenAPlanWithNeitherABaselineNorARefusal_WhenItsRefusalIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.refusalOf(plan(29))))
                .isEmpty();
    }

    @Test
    void GivenAPlanWithABaseline_WhenItsRefusalIsLoaded_ThenThereIsNone() {
        PlanId plan = plan(30);
        transactions.inTransaction(() -> repository.add(Baseline.compute(plan, BASELINE_FIGURES, PUBLISHED_AT)));

        assertThat(transactions.inTransaction(() -> repository.refusalOf(plan))).isEmpty();
    }

    @Test
    void GivenAPlanWithARecordedRefusal_WhenABaselineIsAdded_ThenStaleVersionAndTheRefusalStands() {
        PlanId plan = plan(31);
        transactions.inTransaction(() -> repository.recordRefusal(plan, RefusalCause.SOLVER_FAILED));

        assertThatThrownBy(() -> transactions.inTransaction(
                        () -> repository.add(Baseline.compute(plan, BASELINE_FIGURES, PUBLISHED_AT))))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Baseline");
                    assertThat(stale.id()).isEqualTo(plan.value());
                });

        assertThat(transactions.inTransaction(() -> repository.get(plan))).isEmpty();
        assertThat(transactions.inTransaction(() -> repository.refusalOf(plan))).contains(RefusalCause.SOLVER_FAILED);
    }

    @Test
    void GivenAPlanWithABaseline_WhenARefusalIsRecorded_ThenStaleVersionAndTheBaselineStands() {
        PlanId plan = plan(32);
        transactions.inTransaction(() -> repository.add(Baseline.compute(plan, BASELINE_FIGURES, PUBLISHED_AT)));

        assertThatThrownBy(() -> transactions.inTransaction(
                        () -> repository.recordRefusal(plan, RefusalCause.VALIDATOR_REJECTED)))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Baseline");
                    assertThat(stale.id()).isEqualTo(plan.value());
                });

        assertThat(transactions.inTransaction(() -> repository.refusalOf(plan))).isEmpty();
        assertThat(transactions.inTransaction(() -> repository.get(plan))).isPresent();
    }

    @Test
    void GivenAnotherDeliveryRecordedThePlansRefusalFirst_WhenOneIsRecorded_ThenStaleVersionAndTheFirstStands() {
        PlanId plan = plan(33);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    transactions.inANewTransaction(() -> repository.recordRefusal(plan, RefusalCause.SOLVER_FAILED));
                    repository.recordRefusal(plan, RefusalCause.NO_SOLUTION_WITHIN_BUDGET);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Baseline");
                    assertThat(stale.id()).isEqualTo(plan.value());
                });

        assertThat(transactions.inTransaction(() -> repository.refusalOf(plan))).contains(RefusalCause.SOLVER_FAILED);
    }

    // --- the load check (ADR-127 decision 1), mirroring the domain's damaged snapshots

    static Stream<Arguments> damagedRows() {
        String namesNothing = "a recomputed baseline names a withdrawn vehicle or an added order";
        String namedTwice = "a baseline names each withdrawn vehicle and added order once";
        return Stream.of(
                Arguments.of(
                        "a recompute naming nothing",
                        1_001,
                        Scenario.PUBLISHED,
                        (Damage) rows -> rows.update("baseline", "cause = 'RECOMPUTED'"),
                        "Baseline",
                        namesNothing),
                Arguments.of(
                        "a vehicle withdrawn twice with two causes",
                        1_002,
                        Scenario.TWO_VEHICLES_WITHDRAWN,
                        (Damage) rows -> rows.withdraw(2, "NO_DRIVER"),
                        "Baseline",
                        namedTwice),
                Arguments.of(
                        "an order added twice",
                        1_003,
                        Scenario.AN_ORDER_ADDED,
                        (Damage) rows -> rows.run("""
                                INSERT INTO reporting.baseline_added_order (baseline_id, position, order_id)
                                SELECT baseline_id, 1, order_id FROM reporting.baseline_added_order
                                WHERE baseline_id = ?
                                """),
                        "Baseline",
                        namedTwice),
                Arguments.of(
                        "a published baseline naming a withdrawn vehicle",
                        1_004,
                        Scenario.PUBLISHED,
                        (Damage) rows -> rows.withdraw(0, "NO_DRIVER"),
                        "Baseline",
                        "a published baseline names no withdrawn vehicle or added order"),
                Arguments.of(
                        "an order planned twice",
                        1_005,
                        Scenario.PUBLISHED,
                        (Damage) rows -> rows.run("""
                                INSERT INTO reporting.baseline_planned_order
                                    (baseline_id, position, order_id, starts_at, ends_at, service, start_date,
                                     start_seconds)
                                SELECT baseline_id, 3, order_id, starts_at, ends_at, service, start_date, start_seconds
                                FROM reporting.baseline_planned_order WHERE baseline_id = ? AND position = 0
                                """),
                        "PlannedOrders",
                        "a stored value is refused with ORDER_COUNTED_TWICE"),
                Arguments.of(
                        "an order's window ending before it starts",
                        1_006,
                        Scenario.PUBLISHED,
                        (Damage) rows ->
                                rows.update("baseline_planned_order", "ends_at = starts_at - interval '1 hour'"),
                        "TimeWindow",
                        "a stored value is refused with INVALID_TIME_WINDOW"),
                Arguments.of(
                        "planned orders whose positions start past the first",
                        1_007,
                        Scenario.PUBLISHED,
                        (Damage) rows -> rows.update("baseline_planned_order", "position = position + 1"),
                        "Baseline",
                        StoredLists.GAP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredBaselineBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Scenario scenario, Damage damage, String subject, String detail) {
        PlanId plan = plan(n);
        transactions.inTransaction(() -> repository.add(scenario.build(plan)));

        damage.apply(new Rows(jdbc, plan));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(plan)))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("a cause no baseline has", 1_101, (Damage)
                        rows -> rows.update("baseline", "cause = 'REVISED'")),
                Arguments.of("a served order with no start", 1_102, (Damage)
                        rows -> rows.update("baseline_planned_order", "start_date = NULL, start_seconds = NULL")),
                Arguments.of("a dropped order with a start", 1_103, (Damage)
                        rows -> rows.update("baseline_planned_order", "service = 'DROPPED'")),
                Arguments.of("a start with no seconds", 1_104, (Damage)
                        rows -> rows.update("baseline_planned_order", "start_seconds = NULL")),
                Arguments.of("a service no order has", 1_105, (Damage)
                        rows -> rows.update("baseline_planned_order", "service = 'LATE'")),
                Arguments.of("a withdrawal cause no vehicle has", 1_106, (Damage) rows -> rows.withdraw(0, "BROKEN")),
                Arguments.of("a refusal beside the baseline", 1_107, (Damage) rows -> rows.run("""
                        UPDATE reporting.baseline_outcome SET outcome = 'REFUSED', refusal_cause = 'SOLVER_FAILED'
                        WHERE plan_id = ?
                        """)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredBaseline_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Damage damage) {
        PlanId plan = plan(n);
        transactions.inTransaction(() -> repository.add(Scenario.PUBLISHED.build(plan)));
        Rows rows = new Rows(jdbc, plan);

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(plan))).isPresent();
    }

    @Test
    void GivenAPlanWithARecordedRefusal_WhenABaselineRowIsWrittenBesideIt_ThenTheDatabaseRefusesTheWrite() {
        PlanId plan = plan(1_108);
        transactions.inTransaction(() -> repository.recordRefusal(plan, RefusalCause.SOLVER_FAILED));

        assertThatThrownBy(() -> new Rows(jdbc, plan).run("""
                        INSERT INTO reporting.baseline (id, version, distance, driver_hours, cause, computed_at)
                        VALUES (?, 0, 0, 0, 'PUBLISHED', now())
                        """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(plan))).isEmpty();
    }

    /** One damage done with SQL to a stored plan's rows. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run on one plan's rows; every statement is the test's own constant. */
    record Rows(JdbcClient jdbc, PlanId plan) {

        /** Sets columns of the plan's rows in one of Reporting's baseline tables. */
        void update(String table, String assignments) {
            String key = table.equals("baseline") ? "id" : "baseline_id";
            jdbc.sql("UPDATE reporting." + table + " SET " + assignments + " WHERE " + key + " = ?")
                    .param(plan.value())
                    .update();
        }

        /** Withdraws vehicle 1 at a position, with a cause. */
        void withdraw(int position, String cause) {
            jdbc.sql("""
                            INSERT INTO reporting.baseline_withdrawal (baseline_id, position, vehicle, cause)
                            VALUES (?, ?, ?, ?)
                            """)
                    .params(plan.value(), position, VEHICLE_1.value(), cause)
                    .update();
        }

        /** Runs a statement whose one parameter is the plan's UUID. */
        void run(String statement) {
            jdbc.sql(statement).param(plan.value()).update();
        }
    }

    // --- keys and stored values

    private static PlanId plan(int n) {
        return PlanId.of(new UUID(KEY_PREFIX, n));
    }

    private long storedVersion(PlanId plan) {
        return jdbc.sql("SELECT version FROM reporting.baseline WHERE id = ?")
                .param(plan.value())
                .query(Long.class)
                .single();
    }
}
