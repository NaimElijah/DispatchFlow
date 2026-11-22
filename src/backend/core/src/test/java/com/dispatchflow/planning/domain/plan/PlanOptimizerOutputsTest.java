package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.JOB;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O6;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aJob;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.at;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dayAt;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.noChanges;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.standardSolution;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.succeededJob;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.uuid;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.planning.domain.drop.ExclusionCause;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * What the optimizer returns and the plan keeps (ADR-112, ADR-113): routes and stops it couldn't
 * time, times past the date's end, the vehicles a solve left out, what a revision tells Reporting,
 * and how many new IDs a result draws.
 */
class PlanOptimizerOutputsTest {

    private static final DriverStanding D3_STANDS = new DriverStanding(D3, true, true);
    private static final List<ExcludedVehicle> V3_HAS_NO_DEPOT =
            List.of(new ExcludedVehicle(V3, ExclusionCause.DEPOT_WITHOUT_LOCATION));

    // ---- untimed routes and stops, and times past the day ------------------------------------------

    @Test
    void GivenARouteWhoseLastStopMovesOff_WhenTheEvaluationLeavesItUntimed_ThenLaterEditsStillApply() {
        Plan plan = aPlan().ready();
        StopId o3 = stopId(plan, O3);
        RouteId v1 = routeId(plan, V1);
        RouteId v2 = routeId(plan, V2);

        plan.moveStop(o3, v1, 2, evaluationOf(plan, plan.afterMove(o3, v1, 2)).build(), TODAY);
        Plan emptied = reload(plan);

        assertThat(routeOf(emptied, V2).stops()).isEmpty();
        assertThat(routeOf(emptied, V2).plan()).isEmpty();
        StopId o1 = stopId(emptied, O1);
        emptied.moveStop(
                o1, v2, 0, evaluationOf(emptied, emptied.afterMove(o1, v2, 0)).build(), TODAY);
        assertThat(routeOf(emptied, V2).plan()).isPresent();
    }

    @Test
    void GivenAStopTheValidatorCouldntTime_WhenAnEditTakesTheEvaluation_ThenThatStopHasNoTimes() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);
        Evaluation evaluation = evaluationOf(plan, plan.afterMove(o1, v2, 1))
                .untimed(o1)
                .withViolation(violation("BR-14", v2))
                .build();

        plan.moveStop(o1, v2, 1, evaluation, TODAY);

        assertThat(routeOf(plan, V2).stops().get(0).plan()).isPresent();
        assertThat(routeOf(plan, V2).stops().get(1).plan()).isEmpty();
        assertThat(routeOf(reload(plan), V2).stops().get(1).plan()).isEmpty();
    }

    @Test
    void GivenAReturnPastTheDay_WhenAnEditTakesTheEvaluation_ThenTheRouteKeepsThatReturn() {
        Plan plan = aPlan().ready();
        StopId o1 = stopId(plan, O1);
        RouteId v2 = routeId(plan, V2);
        Evaluation evaluation = evaluationOf(plan, plan.afterMove(o1, v2, 1))
                .returningAt(88_200)
                .withViolation(violation("BR-06", v2))
                .build();

        plan.moveStop(o1, v2, 1, evaluation, TODAY);

        SolveTime returnsAt = routeOf(reload(plan), V2).plan().orElseThrow().returnsAt();
        assertThat(returnsAt).isEqualTo(at(88_200));
        assertThat(returnsAt.pastTheDay()).isTrue();
    }

    @Test
    void GivenAShiftBrokenPastMidnight_WhenAHandOverIsConfirmed_ThenTheConfirmedReturnIsPastTheDay() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        Evaluation breaks = evaluationOf(plan, plan.afterHandOver(v1, D3))
                .withViolation(violation("BR-06", v1))
                .returningAt(88_200)
                .build();

        plan.handOver(v1, D3_STANDS, Optional.of(breaks), true, DISPATCHER, TODAY, LATER);

        assertThat(routeOf(reload(plan), V1).confirmedReturn()).contains(at(88_200));
    }

    @Test
    void GivenADailyResultWithALateShiftDrop_WhenApplied_ThenTheDropKeepsItsFactPastTheDay() {
        DropReason lateShift = new DropReason.Shift(dayAt(84_600), at(88_200));
        ValidatedSolution solution = solution(
                List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)),
                List.of(dropped(O4, lateShift), dropped(O5, new DropReason.MissingLocation())),
                List.of());

        Plan plan = aPlan().solvedAs(solution).ready();

        assertThat(plan.snapshot().dropped()).contains(dropped(O4, lateShift));
    }

    @Test
    void GivenADailyResultWithARouteThatHasNoStops_WhenApplied_ThenThatRouteHasNoTimes() {
        ValidatedSolution solution = solution(
                List.of(solved(V1, D1, O1, O2, O3), solved(V2, D2)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                List.of());

        Plan plan = aPlan().solvedAs(solution).ready();

        assertThat(routeOf(plan, V1).plan()).isPresent();
        assertThat(routeOf(plan, V2).plan()).isEmpty();
    }

    // ---- the vehicles a solve left out -------------------------------------------------------------

    @Test
    void GivenADailySolveThatLeftAVehicleOut_WhenItsJobSucceeds_ThenItsResultNamesTheVehicle() {
        SolveJob job = succeededJob(JOB, standardSolutionWithout(V3_HAS_NO_DEPOT));

        assertThat(job.result().orElseThrow().excluded()).isEqualTo(V3_HAS_NO_DEPOT);
    }

    @Test
    void GivenADailyResultThatLeftAVehicleOut_WhenApplied_ThenThePlanNamesItWithItsCause() {
        Plan plan = aPlan().solvedAs(standardSolutionWithout(V3_HAS_NO_DEPOT)).ready();

        assertThat(plan.snapshot().excluded()).isEqualTo(V3_HAS_NO_DEPOT);
    }

    @Test
    void GivenAProposalThatLeftVehiclesOut_WhenAccepted_ThenTheyReplaceThePlansExclusions() {
        Plan plan = aPlan().solvedAs(standardSolutionWithout(V3_HAS_NO_DEPOT)).published();
        List<ExcludedVehicle> excluded = List.of(new ExcludedVehicle(V3, ExclusionCause.VEHICLE_UNAVAILABLE));
        ReoptimizationProposal proposal = proposal(solution(
                List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                excluded));

        plan.accept(proposal, noChanges(V1, V2), freshIds(700), DISPATCHER, TODAY, LATER);

        assertThat(proposal.snapshot().excluded()).isEqualTo(excluded);
        assertThat(reload(plan).snapshot().excluded()).isEqualTo(excluded);
    }

    @Test
    void GivenADraftThatNamesAnExcludedVehicle_WhenRestored_ThenStoredStateInvalid() {
        PlanSnapshot draft = aPlan().draft().snapshot();
        PlanSnapshot broken = new PlanSnapshot(
                draft.id(),
                draft.version(),
                draft.date(),
                draft.openedAt(),
                draft.status(),
                draft.applied(),
                draft.routes(),
                draft.dropped(),
                V3_HAS_NO_DEPOT,
                draft.pins(),
                draft.violations(),
                draft.undo(),
                draft.redo());

        assertThatThrownBy(() -> Plan.restore(broken))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains("a draft plan has no routes"));
    }

    // ---- what a revision tells Reporting -----------------------------------------------------------

    @Test
    void GivenAProposalThatWithdrawsAVehicle_WhenAccepted_ThenTheRevisionNamesItWithItsCause() {
        Plan plan = aPlan().published();
        ReoptimizationProposal proposal = proposal(
                solution(
                        List.of(solved(V1, D1), solved(V2, D2, O3, O2)),
                        List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                        List.of()),
                new VehicleWithdrawal(V1, WithdrawalCause.SHIFT_RAN_OUT));

        plan.accept(proposal, noChanges(V2), freshIds(700), DISPATCHER, TODAY, LATER);

        PlanRevised revised = (PlanRevised) plan.pendingEvents().get(0);
        assertThat(revised.withdrawnVehicles()).containsExactly(V1);
        assertThat(revised.withdrawalCauses()).containsExactly(WithdrawalCause.SHIFT_RAN_OUT);
        assertThat(revised.addedOrders()).isEmpty();
    }

    @Test
    void GivenAProposalThatServesANewOrder_WhenAccepted_ThenTheRevisionNamesTheAddedOrder() {
        Plan plan = aPlan().published();
        ReoptimizationProposal proposal = proposal(solution(
                List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3, O6)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                List.of()));

        plan.accept(proposal, noChanges(V1, V2), freshIds(700), DISPATCHER, TODAY, LATER);

        PlanRevised revised = (PlanRevised) plan.pendingEvents().get(0);
        assertThat(revised.addedOrders()).containsExactly(O6);
        assertThat(revised.withdrawnVehicles()).isEmpty();
        assertThat(revised.withdrawalCauses()).isEmpty();
    }

    // ---- how many new IDs a result draws ------------------------------------------------------------

    @Test
    void GivenOneNewIdPerRouteAndStopOfTheResult_WhenANewPlansResultIsApplied_ThenItIsReady() {
        Plan plan = aPlan().solving();

        plan.applySolveResult(succeededJob(JOB, standardSolution()), ids(5), NOW);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Ready());
    }

    @Test
    void GivenOneNewIdTooFew_WhenANewPlansResultIsApplied_ThenTheSupplyRunsOutAndNothingChanges() {
        Plan plan = aPlan().solving();
        PlanSnapshot before = plan.snapshot();

        assertThatThrownBy(() -> plan.applySolveResult(succeededJob(JOB, standardSolution()), ids(4), NOW))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(plan.snapshot()).isEqualTo(before);
    }

    @Test
    void GivenOneNewIdPerRouteAndStopOfTheProposal_WhenAccepted_ThenItIsTheNextRevision() {
        Plan plan = aPlan().published();
        ReoptimizationProposal proposal = proposal(solution(
                List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3), solved(V3, D3, O4)),
                List.of(dropped(O5, new DropReason.MissingLocation())),
                List.of()));

        plan.accept(proposal, noChanges(V1, V2, V3), ids(7), DISPATCHER, TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(2)));
    }

    private static ValidatedSolution standardSolutionWithout(List<ExcludedVehicle> excluded) {
        return solution(
                List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                excluded);
    }

    private static ValidatedSolution solution(
            List<SolvedRoute> routes, List<DroppedOrder> drops, List<ExcludedVehicle> excluded) {
        return ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, excluded));
    }

    private static ReoptimizationProposal proposal(ValidatedSolution solution, VehicleWithdrawal... withdrawn) {
        SolveJob job = aJob().reoptimizing().running();
        return job.succeed(PROPOSAL, reoptimizationResult(solution, withdrawn), LATER);
    }

    private static FreshIds ids(int count) {
        List<UUID> uuids =
                LongStream.range(0, count).mapToObj(n -> uuid(90_000 + n)).toList();
        return FreshIds.of(uuids);
    }
}
