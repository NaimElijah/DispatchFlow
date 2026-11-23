package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertLeftAsItWas;
import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertPlanRefused;
import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertUnchanged;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DATE;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.JOB;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.OTHER_PLAN;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.SECOND_JOB;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TOMORROW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.ordersOn;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.roomIn;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.standardSolution;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.succeededJob;
import static com.dispatchflow.shared.AsSaved.asSaved;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.dispatchflow.planning.domain.PlanningError.CapacityForAnotherLane;
import com.dispatchflow.planning.domain.PlanningError.DateInPast;
import com.dispatchflow.planning.domain.PlanningError.PlanAlreadyExists;
import com.dispatchflow.planning.domain.PlanningError.PlanNotDraftOrReady;
import com.dispatchflow.planning.domain.PlanningError.PlanNotPublished;
import com.dispatchflow.planning.domain.PlanningError.ReoptimizationAlreadyRunning;
import com.dispatchflow.planning.domain.PlanningError.RouteLocked;
import com.dispatchflow.planning.domain.PlanningError.SolveQueueFull;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.basis.SolveBasis;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.revision.PlanRevision;
import com.dispatchflow.planning.domain.solution.SolveFailure;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.SolveFailureReason;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanSolvingTest {

    private static final List<DroppedOrder> BOTH_DROPS =
            List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation()));

    private static final LaneCapacity FULL_DAILY_LANE = LaneCapacity.readUnderLock(
            Lane.DAILY,
            List.of(
                    PlanId.of(PlanningFixtures.uuid(901)),
                    PlanId.of(PlanningFixtures.uuid(902)),
                    PlanId.of(PlanningFixtures.uuid(903)),
                    PlanId.of(PlanningFixtures.uuid(904)),
                    PlanId.of(PlanningFixtures.uuid(905))),
            List.of());

    @Test
    void GivenAFreeDate_WhenAPlanIsOpened_ThenItIsADraftWithNothingInIt() {
        Plan plan = Plan.open(PLAN, DATE, PlansOnDate.none(), TODAY, NOW);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Draft());
        assertThat(PlanDescription.of(plan).routes()).isEmpty();
        assertThat(plan.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADateWithAnUncancelledPlan_WhenAPlanIsOpened_ThenPlanAlreadyExists() {
        assertRefused(
                () -> Plan.open(PLAN, DATE, PlansOnDate.of(OTHER_PLAN), TODAY, NOW),
                new PlanAlreadyExists(DATE, OTHER_PLAN));
    }

    @Test
    void GivenAPastDate_WhenAPlanIsOpened_ThenDateInPast() {
        DeliveryDate yesterday = DeliveryDate.of(TODAY.minusDays(1));

        assertRefused(() -> Plan.open(PLAN, yesterday, PlansOnDate.none(), TODAY, NOW), new DateInPast(yesterday));
    }

    @Test
    void GivenADraft_WhenASolveIsRequested_ThenItIsSolvingForTheNewQueuedDailyJob() {
        Plan plan = aPlan().draft();

        SolveJob job = plan.requestSolve(
                JOB, SolverName.OR_TOOLS, SolveLimit.DEFAULT_DAILY, Seed.of(7), roomIn(Lane.DAILY), TODAY, NOW);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Solving(new SolveBasis(JOB, NOW)));
        assertThat(job.phase()).isEqualTo(new SolveJob.Phase.Queued());
        assertThat(job.lane()).isEqualTo(Lane.DAILY);
        assertThat(job.plan()).isEqualTo(PLAN);
    }

    @Test
    void GivenAPlanWhoseDateHasPassed_WhenASolveIsRequested_ThenDateInPast() {
        Plan plan = aPlan().ready();

        assertPlanRefused(
                plan,
                () -> plan.requestSolve(
                        SECOND_JOB,
                        SolverName.OR_TOOLS,
                        SolveLimit.DEFAULT_DAILY,
                        Seed.of(7),
                        roomIn(Lane.DAILY),
                        TOMORROW,
                        NOW),
                new DateInPast(DATE));
    }

    @Test
    void GivenAFullDailyLane_WhenASolveIsRequested_ThenSolveQueueFull() {
        Plan plan = aPlan().draft();

        assertPlanRefused(
                plan,
                () -> plan.requestSolve(
                        JOB, SolverName.OR_TOOLS, SolveLimit.DEFAULT_DAILY, Seed.of(7), FULL_DAILY_LANE, TODAY, NOW),
                new SolveQueueFull(Lane.DAILY, 5));
    }

    @Test
    void GivenTheReoptimizationLanesCapacity_WhenADailySolveIsRequested_ThenCapacityForAnotherLane() {
        Plan plan = aPlan().draft();

        assertPlanRefused(
                plan,
                () -> plan.requestSolve(
                        JOB,
                        SolverName.OR_TOOLS,
                        SolveLimit.DEFAULT_DAILY,
                        Seed.of(7),
                        roomIn(Lane.REOPTIMIZATION),
                        TODAY,
                        NOW),
                new CapacityForAnotherLane(Lane.DAILY, Lane.REOPTIMIZATION));
    }

    @Test
    void GivenAPublishedPlan_WhenASolveIsRequested_ThenPlanNotDraftOrReady() {
        Plan plan = aPlan().published();

        assertPlanRefused(
                plan,
                () -> plan.requestSolve(
                        SECOND_JOB,
                        SolverName.OR_TOOLS,
                        SolveLimit.DEFAULT_DAILY,
                        Seed.of(7),
                        roomIn(Lane.DAILY),
                        TODAY,
                        NOW),
                new PlanNotDraftOrReady(PLAN));
    }

    @Test
    void
            GivenAPublishedPlan_WhenAReoptimizationIsRequested_ThenANewJobIsQueuedWithTwentySecondsAndThePlanIsUnchanged() {
        Plan plan = aPlan().published();
        PlanDescription before = PlanDescription.of(plan);
        Optional<PlanRevision> pending = plan.pendingRevision();

        SolveJob job = plan.requestReoptimization(
                SECOND_JOB, SolverName.OR_TOOLS, Seed.of(9), roomIn(Lane.REOPTIMIZATION), TODAY, LATER);

        assertThat(job.lane()).isEqualTo(Lane.REOPTIMIZATION);
        assertThat(job.limit()).isEqualTo(SolveLimit.REOPTIMIZATION);
        assertThat(job.basisRevision()).contains(RevisionNo.FIRST);
        assertLeftAsItWas(plan, before, pending);
    }

    @Test
    void GivenAReadyPlan_WhenAReoptimizationIsRequested_ThenPlanNotPublished() {
        Plan plan = aPlan().ready();

        assertPlanRefused(
                plan,
                () -> plan.requestReoptimization(
                        SECOND_JOB, SolverName.OR_TOOLS, Seed.of(9), roomIn(Lane.REOPTIMIZATION), TODAY, LATER),
                new PlanNotPublished(PLAN));
    }

    @Test
    void GivenAQueuedReoptimizationOfThePlan_WhenAnotherIsRequested_ThenReoptimizationAlreadyRunning() {
        Plan plan = aPlan().published();
        LaneCapacity busy = LaneCapacity.readUnderLock(Lane.REOPTIMIZATION, List.of(PLAN), List.of());

        assertPlanRefused(
                plan,
                () -> plan.requestReoptimization(SECOND_JOB, SolverName.OR_TOOLS, Seed.of(9), busy, TODAY, LATER),
                new ReoptimizationAlreadyRunning(PLAN));
    }

    @Test
    void GivenARunningReoptimizationOfThePlan_WhenAnotherIsRequested_ThenReoptimizationAlreadyRunning() {
        Plan plan = aPlan().published();
        LaneCapacity busy = LaneCapacity.readUnderLock(Lane.REOPTIMIZATION, List.of(), List.of(PLAN));

        assertPlanRefused(
                plan,
                () -> plan.requestReoptimization(SECOND_JOB, SolverName.OR_TOOLS, Seed.of(9), busy, TODAY, LATER),
                new ReoptimizationAlreadyRunning(PLAN));
    }

    @Test
    void GivenTheJobItIsSolvingFor_WhenItsResultIsApplied_ThenThePlanIsReadyWithItsRoutesAndDrops() {
        Plan plan = aPlan().solving();

        plan.applySolveResult(succeededJob(JOB, standardSolution()), freshIds(0), LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Ready());
        assertThat(ordersOn(plan, V1)).containsExactly(O1, O2);
        assertThat(ordersOn(plan, V2)).containsExactly(O3);
        assertThat(plan.dropped()).extracting(DroppedOrder::order).containsExactlyInAnyOrder(O4, O5);
        assertThat(plan.applied()).contains(new AppliedSolve(new SolveBasis(JOB, NOW), LATER));
        assertThat(PlanDescription.of(plan).routes())
                .extracting(PlanDescription.RouteDescription::index, PlanDescription.RouteDescription::check)
                .containsExactly(
                        tuple(RouteIndex.of(1), CheckState.CHECKED), tuple(RouteIndex.of(2), CheckState.CHECKED));
        assertThat(plan.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAnotherJobsResult_WhenApplied_ThenNothingChanges() {
        Plan plan = aPlan().solving();
        PlanDescription before = PlanDescription.of(plan);

        plan.applySolveResult(succeededJob(SECOND_JOB, standardSolution()), freshIds(0), LATER);

        assertUnchanged(plan, before);
    }

    @Test
    void GivenAReadyPlan_WhenItsAppliedJobsResultArrivesAgain_ThenNothingChanges() {
        Plan plan = aPlan().ready();
        PlanDescription before = PlanDescription.of(plan);

        plan.applySolveResult(succeededJob(JOB, standardSolution()), freshIds(500), LATER);

        assertUnchanged(plan, before);
    }

    @Test
    void GivenAnAbandonedPlanThatWasSolving_WhenItsJobsLateSuccessArrives_ThenNothingChanges() {
        Plan plan = aPlan().solving();
        plan.abandon(TOMORROW);
        Plan abandoned = asSaved(plan);
        PlanDescription before = PlanDescription.of(abandoned);

        abandoned.applySolveResult(succeededJob(JOB, standardSolution()), freshIds(0), LATER);

        assertUnchanged(abandoned, before);
    }

    @Test
    void GivenAReadyPlanSolvedAgain_WhenTheNewResultIsApplied_ThenEachVehicleKeepsItsRouteIdIndexAndStops() {
        Plan ready = aPlan().ready();
        RouteId v1Route = routeId(ready, V1);
        RouteIndex v2Index = routeOf(ready, V2).index();
        StopId o1Stop = stopId(ready, O1);
        Plan plan = aPlan().solvingAgain();
        ValidatedSolution resolved = ValidatedSolution.of(ValidationReport.of(
                List.of(),
                List.of(solved(V3, PlanningFixtures.D3, O4), solved(V2, D2, O3), solved(V1, D1, O2, O1)),
                List.of(dropped(O5, new DropReason.MissingLocation())),
                List.of()));

        plan.applySolveResult(succeededJob(SECOND_JOB, resolved), freshIds(500), LATER);

        assertThat(routeId(plan, V1)).isEqualTo(v1Route);
        assertThat(routeOf(plan, V2).index()).isEqualTo(v2Index);
        assertThat(stopId(plan, O1)).isEqualTo(o1Stop);
        assertThat(routeOf(plan, V3).index()).isEqualTo(RouteIndex.of(3));
        assertThat(ordersOn(plan, V1)).containsExactly(O2, O1);
    }

    /** V1/D1 serves O1, O2; V2/D2 serves O3; V3/D3 drives an empty route; O4 and O5 are dropped. */
    private static ValidatedSolution threeRoutesWithV3Empty() {
        return ValidatedSolution.of(ValidationReport.of(
                List.of(), List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3), solved(V3, D3)), BOTH_DROPS, List.of()));
    }

    /** A plan solved as {@code solution}, one vehicle's route locked, solving again for the second job. */
    private static Plan solvingAgainWithLock(VehicleId vehicle, RouteLock lock, ValidatedSolution solution) {
        Plan ready = aPlan().solvedAs(solution).ready();
        ready.lockRoute(routeId(ready, vehicle), lock, TODAY);
        Plan locked = asSaved(ready);
        locked.requestSolve(
                SECOND_JOB,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(8),
                roomIn(Lane.DAILY),
                TODAY,
                LATER);
        return asSaved(locked);
    }

    @Test
    void GivenAReadyPlanWithEdits_WhenANewSolveIsApplied_ThenTheJournalIsCleared() {
        Plan ready = aPlan().ready();
        ready.lockRoute(routeId(ready, V2), RouteLock.WHOLE_ROUTE, TODAY);
        ready.unlockRoute(routeId(ready, V2), TODAY);
        Plan edited = asSaved(ready);
        edited.requestSolve(
                SECOND_JOB,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(8),
                roomIn(Lane.DAILY),
                TODAY,
                LATER);
        Plan plan = asSaved(edited);

        plan.applySolveResult(succeededJob(SECOND_JOB, standardSolution()), freshIds(500), LATER);

        assertThat(plan.undo()).isEmpty();
        assertThat(plan.redo()).isEmpty();
    }

    @Test
    void GivenAWholeRouteLock_WhenANewResultChangesThatRoute_ThenRouteLocked() {
        Plan ready = aPlan().ready();
        RouteId locked = routeId(ready, V2);
        ready.lockRoute(locked, RouteLock.WHOLE_ROUTE, TODAY);
        Plan relocked = asSaved(ready);
        relocked.requestSolve(
                SECOND_JOB,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(8),
                roomIn(Lane.DAILY),
                TODAY,
                LATER);
        Plan plan = asSaved(relocked);
        ValidatedSolution changed = ValidatedSolution.of(ValidationReport.of(
                List.of(), List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3, O4)), List.of(), List.of()));

        assertPlanRefused(
                plan,
                () -> plan.applySolveResult(succeededJob(SECOND_JOB, changed), freshIds(500), LATER),
                new RouteLocked(locked));
    }

    @Test
    void GivenASequenceLock_WhenANewResultServesTheSequenceOnAnotherVehicle_ThenTheLockMovesWithIt() {
        Plan ready = aPlan().ready();
        ready.lockRoute(routeId(ready, V2), RouteLock.STOP_SEQUENCE, TODAY);
        Plan relocked = asSaved(ready);
        relocked.requestSolve(
                SECOND_JOB,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(8),
                roomIn(Lane.DAILY),
                TODAY,
                LATER);
        Plan plan = asSaved(relocked);
        ValidatedSolution moved = ValidatedSolution.of(ValidationReport.of(
                List.of(), List.of(solved(V1, D1, O1, O2), solved(V3, PlanningFixtures.D3, O3)), List.of(), List.of()));

        plan.applySolveResult(succeededJob(SECOND_JOB, moved), freshIds(500), LATER);

        assertThat(routeOf(plan, V3).lock()).contains(RouteLock.STOP_SEQUENCE);
        assertThat(routeOf(plan, V1).lock()).isEmpty();
    }

    @Test
    void GivenAClockThatSteppedBack_WhenTheResultIsAppliedBeforeTheRequest_ThenItCountsAsAppliedAtTheRequest() {
        Plan plan = aPlan().solving();

        plan.applySolveResult(succeededJob(JOB, standardSolution()), freshIds(0), NOW.minusSeconds(1));

        assertThat(plan.applied()).contains(new AppliedSolve(new SolveBasis(JOB, NOW), NOW));
        assertThat(plan.status()).isEqualTo(new Plan.Status.Ready());
    }

    @Test
    void GivenASequenceLockOnAnEmptyRoute_WhenANewResultLeavesTwoRoutesEmpty_ThenOnlyThatVehiclesRouteKeepsTheLock() {
        Plan plan = solvingAgainWithLock(V3, RouteLock.STOP_SEQUENCE, threeRoutesWithV3Empty());
        ValidatedSolution twoEmpty = ValidatedSolution.of(ValidationReport.of(
                List.of(), List.of(solved(V1, D1, O1, O2, O3), solved(V2, D2), solved(V3, D3)), BOTH_DROPS, List.of()));

        plan.applySolveResult(succeededJob(SECOND_JOB, twoEmpty), freshIds(500), LATER);

        assertThat(routeOf(plan, V3).lock()).contains(RouteLock.STOP_SEQUENCE);
        assertThat(routeOf(plan, V2).lock()).isEmpty();
        assertThat(routeOf(plan, V1).lock()).isEmpty();
    }

    @Test
    void GivenASequenceLockOnAnEmptyRoute_WhenANewResultGivesThatVehicleStops_ThenRouteLocked() {
        Plan plan = solvingAgainWithLock(V3, RouteLock.STOP_SEQUENCE, threeRoutesWithV3Empty());
        RouteId v3 = routeId(plan, V3);
        ValidatedSolution filled = ValidatedSolution.of(ValidationReport.of(
                List.of(), List.of(solved(V1, D1, O1, O2), solved(V2, D2), solved(V3, D3, O3)), BOTH_DROPS, List.of()));

        assertPlanRefused(
                plan,
                () -> plan.applySolveResult(succeededJob(SECOND_JOB, filled), freshIds(500), LATER),
                new RouteLocked(v3));
    }

    @Test
    void GivenALockTheResultKeeps_WhenThePlanIsAskedForTheLocksItBreaks_ThenNone() {
        Plan plan = solvingAgainWithLock(V2, RouteLock.WHOLE_ROUTE, standardSolution());

        assertThat(plan.locksBrokenBy(standardSolution())).isEmpty();
    }

    @Test
    void GivenLocksTheResultBreaks_WhenThePlanIsAskedForTheLocksItBreaks_ThenEachIsAnFr147ViolationOnItsRoute() {
        Plan ready = aPlan().ready();
        RouteId v1 = routeId(ready, V1);
        RouteId v2 = routeId(ready, V2);
        ready.lockRoute(v1, RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = asSaved(ready);
        locked.lockRoute(v2, RouteLock.STOP_SEQUENCE, TODAY);
        Plan plan = asSaved(locked);
        ValidatedSolution breaksBoth = ValidatedSolution.of(ValidationReport.of(
                List.of(),
                List.of(solved(V1, D1, O2, O1), solved(V2, D2, O3, O4)),
                List.of(BOTH_DROPS.get(1)),
                List.of()));

        assertThat(plan.locksBrokenBy(breaksBoth))
                .containsExactly(Violation.onRoute(RuleCode.LOCKS, v1), Violation.onRoute(RuleCode.LOCKS, v2));
        assertThat(SolveFailure.lockNotHonoured(plan.locksBrokenBy(breaksBoth)).reason())
                .isEqualTo(SolveFailureReason.LOCK_NOT_HONOURED);
    }

    @Test
    void GivenAPlanSolvingForTheFirstTime_WhenItsJobFails_ThenItIsADraftAgain() {
        Plan plan = aPlan().solving();

        plan.solveFailed(JOB);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Draft());
    }

    @Test
    void GivenAPlanSolvedBefore_WhenItsNewJobFails_ThenItIsReadyWithItsRoutesAsTheyWere() {
        Plan plan = aPlan().solvingAgain();
        List<PlanDescription.RouteDescription> routes = PlanDescription.of(plan).routes();

        plan.solveFailed(SECOND_JOB);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Ready());
        assertThat(PlanDescription.of(plan).routes()).isEqualTo(routes);
    }

    @Test
    void GivenAnotherJob_WhenItFails_ThenNothingChanges() {
        Plan plan = aPlan().solving();
        PlanDescription before = PlanDescription.of(plan);

        plan.solveFailed(SECOND_JOB);

        assertUnchanged(plan, before);
    }

    @Test
    void GivenAnAbandonedPlan_WhenALateFailureArrives_ThenNothingChanges() {
        Plan plan = aPlan().abandoned();
        PlanDescription before = PlanDescription.of(plan);

        plan.solveFailed(JOB);

        assertUnchanged(plan, before);
    }
}
