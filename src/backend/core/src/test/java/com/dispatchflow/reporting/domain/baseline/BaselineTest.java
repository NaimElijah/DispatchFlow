package com.dispatchflow.reporting.domain.baseline;

import static com.dispatchflow.reporting.domain.ReportingFixtures.BASELINE_FIGURES;
import static com.dispatchflow.reporting.domain.ReportingFixtures.BASELINE_WITH_ADDED_ORDER;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_C;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_D;
import static com.dispatchflow.reporting.domain.ReportingFixtures.PLAN;
import static com.dispatchflow.reporting.domain.ReportingFixtures.PLAN_FIGURES;
import static com.dispatchflow.reporting.domain.ReportingFixtures.PLAN_WITH_ADDED_ORDER;
import static com.dispatchflow.reporting.domain.ReportingFixtures.RECOMPUTED_FIGURES;
import static com.dispatchflow.reporting.domain.ReportingFixtures.SHIFT_RAN_OUT_2;
import static com.dispatchflow.reporting.domain.ReportingFixtures.UNAVAILABLE_1;
import static com.dispatchflow.reporting.domain.ReportingFixtures.VEHICLE_1;
import static com.dispatchflow.reporting.domain.ReportingFixtures.VEHICLE_2;
import static com.dispatchflow.reporting.domain.baseline.BaselineBuilder.COMPUTED_AT;
import static com.dispatchflow.reporting.domain.baseline.BaselineBuilder.LOADED;
import static com.dispatchflow.reporting.domain.baseline.BaselineBuilder.aBaseline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.reporting.domain.CancelledOrders;
import com.dispatchflow.reporting.domain.PlannedOnTimeRate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A plan's baseline (DL §4.7): computed once when the plan is published (FR-39, FR-342), recomputed
 * only when a re-optimization withdraws a vehicle (FR-267 to FR-271), compared on kilometres and
 * on-time only after that (FR-269), not compared for a cancelled plan (FR-338), and leaving out the
 * orders cancelled since (BR-30).
 */
class BaselineTest {

    private static final Instant NOW = Instant.parse("2027-06-13T10:00:00Z");

    private static final BaselineComparison.AllMeasures PUBLISHED_COMPARISON = new BaselineComparison.AllMeasures(
            Distance.of(84_250),
            Distance.of(97_600),
            DriverHours.of(Duration.ofMinutes(390)),
            DriverHours.of(Duration.ofMinutes(435)),
            new PlannedOnTimeRate.Measured(1, 3),
            new PlannedOnTimeRate.Measured(3, 3));

    private static final BaselineComparison.KilometresAndOnTime WITHDRAWN_COMPARISON =
            new BaselineComparison.KilometresAndOnTime(
                    Distance.of(84_250),
                    Distance.of(91_000),
                    new PlannedOnTimeRate.Measured(1, 3),
                    new PlannedOnTimeRate.Measured(2, 3),
                    List.of(VEHICLE_1));

    private static BaselineBuilder published() {
        return aBaseline();
    }

    private static BaselineBuilder withdrawnOne() {
        return aBaseline().withdrawn(UNAVAILABLE_1).withResult(RECOMPUTED_FIGURES);
    }

    // --- compute (FR-39, FR-342)

    @Test
    void GivenAPublishedPlan_WhenItsBaselineIsComputed_ThenItHoldsTheSolveAsPublishedAndNew() {
        Baseline baseline = Baseline.compute(PLAN, BASELINE_FIGURES, NOW);

        assertThat(baseline.plan()).isEqualTo(PLAN);
        assertThat(baseline.version()).isEqualTo(Version.NEW);
        assertThat(baseline.snapshot())
                .isEqualTo(
                        new BaselineSnapshot(PLAN, Version.NEW, BASELINE_FIGURES, new BaselineCause.Published(), NOW));
        assertThat(baseline.pendingEvents()).isEmpty();
    }

    // --- recompute (FR-267 to FR-271)

    @Test
    void GivenAPublishedBaseline_WhenAVehicleIsWithdrawn_ThenTheNewSolveAndTheWithdrawalWithItsCauseAreKept() {
        Baseline baseline = published().build();

        baseline.recompute(List.of(UNAVAILABLE_1), List.of(), RECOMPUTED_FIGURES, NOW);

        assertThat(baseline.snapshot())
                .isEqualTo(new BaselineSnapshot(
                        PLAN,
                        LOADED,
                        RECOMPUTED_FIGURES,
                        new BaselineCause.Recomputed(List.of(UNAVAILABLE_1), List.of()),
                        NOW));
        assertThat(baseline.pendingEvents()).isEmpty();
    }

    @Test
    void
            GivenAVehicleAlreadyWithdrawn_WhenAnotherIsWithdrawnAndOneRepeated_ThenEachIsNamedOnceInTheOrderFirstWithdrawn() {
        Baseline baseline = withdrawnOne().build();
        Withdrawal vehicle1Again = new Withdrawal(VEHICLE_1, WithdrawalCause.SHIFT_RAN_OUT);

        baseline.recompute(List.of(SHIFT_RAN_OUT_2, SHIFT_RAN_OUT_2, vehicle1Again), List.of(), BASELINE_FIGURES, NOW);

        assertThat(baseline.snapshot().cause())
                .isEqualTo(new BaselineCause.Recomputed(List.of(UNAVAILABLE_1, SHIFT_RAN_OUT_2), List.of()));
    }

    // --- the fleet the next solve treats (FR-271)

    @Test
    void
            GivenAVehicleWithdrawnAsUnavailable_WhenAnotherRunsOutOfShift_ThenTheSolveLeavesOutTheFirstAndKeepsTheSecond() {
        Baseline baseline = withdrawnOne().build();
        BaselineSnapshot before = baseline.snapshot();

        WithdrawnFleet fleet = baseline.withdrawnFleetAfter(List.of(SHIFT_RAN_OUT_2));

        assertThat(fleet).isEqualTo(new WithdrawnFleet(List.of(VEHICLE_1), List.of(VEHICLE_2)));
        assertThat(baseline.snapshot()).isEqualTo(before);
    }

    @Test
    void GivenAPublishedBaseline_WhenARevisionAddsOnlyOrders_ThenTheSolveTreatsNoVehicleAsWithdrawn() {
        assertThat(published().build().withdrawnFleetAfter(List.of()))
                .isEqualTo(new WithdrawnFleet(List.of(), List.of()));
    }

    @Test
    void GivenAVehicleWithdrawnAsUnavailable_WhenItIsNamedAgainAsShiftRanOut_ThenItStaysLeftOut() {
        Baseline baseline = withdrawnOne().build();

        WithdrawnFleet fleet =
                baseline.withdrawnFleetAfter(List.of(new Withdrawal(VEHICLE_1, WithdrawalCause.SHIFT_RAN_OUT)));

        assertThat(fleet).isEqualTo(new WithdrawnFleet(List.of(VEHICLE_1), List.of()));
    }

    @Test
    void GivenARevisionThatWithdrewNoVehicle_WhenTheBaselineIsAskedToRecompute_ThenNothingChanges() {
        Baseline baseline = published().build();
        BaselineSnapshot before = baseline.snapshot();

        baseline.recompute(List.of(), List.of(), RECOMPUTED_FIGURES, NOW);

        assertThat(baseline.snapshot()).isEqualTo(before);
        assertThat(baseline.pendingEvents()).isEmpty();
    }

    // --- every state × command

    static Stream<Arguments> recomputeCells() {
        return Stream.of(
                Arguments.of(
                        "published, a vehicle withdrawn",
                        (Supplier<BaselineBuilder>) BaselineTest::published,
                        List.of(SHIFT_RAN_OUT_2),
                        List.of(),
                        recomputedAtNow(List.of(SHIFT_RAN_OUT_2), List.of())),
                Arguments.of(
                        "published, an order added",
                        (Supplier<BaselineBuilder>) BaselineTest::published,
                        List.of(),
                        List.of(ORDER_D),
                        recomputedAtNow(List.of(), List.of(ORDER_D))),
                Arguments.of(
                        "published, a vehicle withdrawn and an order added",
                        (Supplier<BaselineBuilder>) BaselineTest::published,
                        List.of(SHIFT_RAN_OUT_2),
                        List.of(ORDER_D),
                        recomputedAtNow(List.of(SHIFT_RAN_OUT_2), List.of(ORDER_D))),
                Arguments.of(
                        "published, nothing withdrawn or added",
                        (Supplier<BaselineBuilder>) BaselineTest::published,
                        List.of(),
                        List.of(),
                        new BaselineSnapshot(
                                PLAN, LOADED, BASELINE_FIGURES, new BaselineCause.Published(), COMPUTED_AT)),
                Arguments.of(
                        "recomputed, another vehicle withdrawn",
                        (Supplier<BaselineBuilder>) BaselineTest::withdrawnOne,
                        List.of(SHIFT_RAN_OUT_2),
                        List.of(),
                        recomputedAtNow(List.of(UNAVAILABLE_1, SHIFT_RAN_OUT_2), List.of())),
                Arguments.of(
                        "recomputed, an order added",
                        (Supplier<BaselineBuilder>) BaselineTest::withdrawnOne,
                        List.of(),
                        List.of(ORDER_D),
                        recomputedAtNow(List.of(UNAVAILABLE_1), List.of(ORDER_D))),
                Arguments.of(
                        "recomputed, nothing withdrawn or added",
                        (Supplier<BaselineBuilder>) BaselineTest::withdrawnOne,
                        List.of(),
                        List.of(),
                        new BaselineSnapshot(
                                PLAN,
                                LOADED,
                                RECOMPUTED_FIGURES,
                                new BaselineCause.Recomputed(List.of(UNAVAILABLE_1), List.of()),
                                COMPUTED_AT)));
    }

    private static BaselineSnapshot recomputedAtNow(List<Withdrawal> withdrawn, List<OrderId> added) {
        return new BaselineSnapshot(
                PLAN, LOADED, RECOMPUTED_FIGURES, new BaselineCause.Recomputed(withdrawn, added), NOW);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recomputeCells")
    void GivenEachBaselineState_WhenRecomputed_ThenTheResultIsTheExpectedState(
            String cell,
            Supplier<BaselineBuilder> state,
            List<Withdrawal> withdrawn,
            List<OrderId> added,
            BaselineSnapshot expected) {
        Baseline baseline = state.get().build();

        baseline.recompute(withdrawn, added, RECOMPUTED_FIGURES, NOW);

        assertThat(baseline.snapshot()).isEqualTo(expected);
        assertThat(baseline.pendingEvents()).isEmpty();
    }

    // --- an order added after publishing (FR-39, ADR-109)

    @Test
    void
            GivenARevisionThatAddedAnOrder_WhenTheBaselineIsRecomputed_ThenTheComparisonCoversTheSameOrdersOnEveryMeasure() {
        Baseline baseline = published().build();

        baseline.recompute(List.of(), List.of(ORDER_D), BASELINE_WITH_ADDED_ORDER, NOW);

        assertThat(baseline.snapshot())
                .isEqualTo(new BaselineSnapshot(
                        PLAN,
                        LOADED,
                        BASELINE_WITH_ADDED_ORDER,
                        new BaselineCause.Recomputed(List.of(), List.of(ORDER_D)),
                        NOW));
        assertThat(baseline.compareWith(PlanStanding.PUBLISHED, PLAN_WITH_ADDED_ORDER, CancelledOrders.none()))
                .isEqualTo(new BaselineComparison.AllMeasures(
                        Distance.of(88_000),
                        Distance.of(99_500),
                        DriverHours.of(Duration.ofMinutes(400)),
                        DriverHours.of(Duration.ofMinutes(450)),
                        new PlannedOnTimeRate.Measured(2, 4),
                        new PlannedOnTimeRate.Measured(4, 4)));
    }

    @Test
    void GivenAnOrderAddedAndAnotherCancelled_WhenCompared_ThenTheCancelledOneIsOnlyLeftOutAndTheAddedOneCounts() {
        Baseline baseline = published().build();
        baseline.recompute(List.of(), List.of(ORDER_D), BASELINE_WITH_ADDED_ORDER, NOW);

        assertThat(baseline.compareWith(
                        PlanStanding.PUBLISHED, PLAN_WITH_ADDED_ORDER, CancelledOrders.of(List.of(ORDER_C))))
                .isEqualTo(new BaselineComparison.AllMeasures(
                        Distance.of(88_000),
                        Distance.of(99_500),
                        DriverHours.of(Duration.ofMinutes(400)),
                        DriverHours.of(Duration.ofMinutes(450)),
                        new PlannedOnTimeRate.Measured(2, 3),
                        new PlannedOnTimeRate.Measured(3, 3)));
    }

    static Stream<Arguments> comparisonCells() {
        return Stream.of(
                Arguments.of(
                        "published baseline, published plan",
                        (Supplier<BaselineBuilder>) BaselineTest::published,
                        PlanStanding.PUBLISHED,
                        PUBLISHED_COMPARISON),
                Arguments.of(
                        "published baseline, completed plan",
                        (Supplier<BaselineBuilder>) BaselineTest::published,
                        PlanStanding.COMPLETED,
                        PUBLISHED_COMPARISON),
                Arguments.of(
                        "published baseline, cancelled plan",
                        (Supplier<BaselineBuilder>) BaselineTest::published,
                        PlanStanding.CANCELLED,
                        new BaselineComparison.NotCompared()),
                Arguments.of(
                        "withdrawn baseline, published plan",
                        (Supplier<BaselineBuilder>) BaselineTest::withdrawnOne,
                        PlanStanding.PUBLISHED,
                        WITHDRAWN_COMPARISON),
                Arguments.of(
                        "withdrawn baseline, completed plan",
                        (Supplier<BaselineBuilder>) BaselineTest::withdrawnOne,
                        PlanStanding.COMPLETED,
                        WITHDRAWN_COMPARISON),
                Arguments.of(
                        "withdrawn baseline, cancelled plan",
                        (Supplier<BaselineBuilder>) BaselineTest::withdrawnOne,
                        PlanStanding.CANCELLED,
                        new BaselineComparison.NotCompared()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("comparisonCells")
    void GivenEachBaselineStateAndPlanStanding_WhenCompared_ThenTheComparisonIsTheExpectedOneAndNothingChanges(
            String cell, Supplier<BaselineBuilder> state, PlanStanding standing, BaselineComparison expected) {
        Baseline baseline = state.get().build();
        BaselineSnapshot before = baseline.snapshot();

        assertThat(baseline.compareWith(standing, PLAN_FIGURES, CancelledOrders.none()))
                .isEqualTo(expected);
        assertThat(baseline.snapshot()).isEqualTo(before);
        assertThat(baseline.pendingEvents()).isEmpty();
    }

    // --- cancelled orders (BR-30)

    @Test
    void GivenAnOrderCancelledAfterTheBaselineWasComputed_WhenCompared_ThenNeitherSideCountsItOnTime() {
        Baseline baseline = published().build();

        BaselineComparison comparison =
                baseline.compareWith(PlanStanding.PUBLISHED, PLAN_FIGURES, CancelledOrders.of(List.of(ORDER_C)));

        assertThat(comparison)
                .isEqualTo(new BaselineComparison.AllMeasures(
                        Distance.of(84_250),
                        Distance.of(97_600),
                        DriverHours.of(Duration.ofMinutes(390)),
                        DriverHours.of(Duration.ofMinutes(435)),
                        new PlannedOnTimeRate.Measured(1, 2),
                        new PlannedOnTimeRate.Measured(2, 2)));
    }

    // --- restore

    static Stream<Arguments> builderStates() {
        return Stream.of(
                Arguments.of("published", (Supplier<BaselineBuilder>) BaselineTest::published),
                Arguments.of("one vehicle withdrawn", (Supplier<BaselineBuilder>) BaselineTest::withdrawnOne),
                Arguments.of("two vehicles withdrawn", (Supplier<BaselineBuilder>)
                        () -> aBaseline().withdrawn(SHIFT_RAN_OUT_2, UNAVAILABLE_1)),
                Arguments.of("an order added", (Supplier<BaselineBuilder>)
                        () -> aBaseline().added(ORDER_D).withResult(BASELINE_WITH_ADDED_ORDER)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builderStates")
    void GivenEachBuilderState_WhenItsSnapshotIsRestored_ThenTheSameStateComesBack(
            String state, Supplier<BaselineBuilder> builder) {
        Baseline baseline = builder.get().build();

        assertThat(Baseline.restore(baseline.snapshot()).snapshot())
                .isEqualTo(builder.get().snapshot());
        assertThat(Baseline.restore(baseline.snapshot()).plan()).isEqualTo(PLAN);
    }

    @Test
    void GivenAStoredRecomputeNamingNothing_WhenRestored_ThenStoredStateInvalid() {
        BaselineSnapshot stored = aBaseline()
                .withCause(new BaselineCause.Recomputed(List.of(), List.of()))
                .snapshot();

        assertThatThrownBy(() -> Baseline.restore(stored)).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
            assertThat(invalid.subject()).isEqualTo("Baseline");
            assertThat(invalid.detail()).isEqualTo("a recomputed baseline names a withdrawn vehicle or an added order");
        });
    }

    @Test
    void GivenAStoredWithdrawalNamingAVehicleTwiceWithTwoCauses_WhenRestored_ThenStoredStateInvalid() {
        BaselineSnapshot stored = aBaseline()
                .withdrawn(UNAVAILABLE_1, SHIFT_RAN_OUT_2, new Withdrawal(VEHICLE_1, WithdrawalCause.NO_DRIVER))
                .snapshot();

        assertThatThrownBy(() -> Baseline.restore(stored)).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
            assertThat(invalid.subject()).isEqualTo("Baseline");
            assertThat(invalid.detail()).isEqualTo("a baseline names each withdrawn vehicle and added order once");
        });
    }

    @Test
    void GivenAStoredRecomputeNamingAnOrderTwice_WhenRestored_ThenStoredStateInvalid() {
        BaselineSnapshot stored = aBaseline().added(ORDER_D, ORDER_C, ORDER_D).snapshot();

        assertThatThrownBy(() -> Baseline.restore(stored)).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
            assertThat(invalid.subject()).isEqualTo("Baseline");
            assertThat(invalid.detail()).isEqualTo("a baseline names each withdrawn vehicle and added order once");
        });
    }

    @Test
    void GivenAnotherPlansSnapshot_WhenRestored_ThenItIsKeyedByThatPlan() {
        PlanId other = PlanId.of(UUID.fromString("0190a5e2-0000-7000-a000-000000000009"));
        BaselineSnapshot stored =
                new BaselineSnapshot(other, LOADED, BASELINE_FIGURES, new BaselineCause.Published(), COMPUTED_AT);

        assertThat(Baseline.restore(stored).plan()).isEqualTo(other);
    }
}
