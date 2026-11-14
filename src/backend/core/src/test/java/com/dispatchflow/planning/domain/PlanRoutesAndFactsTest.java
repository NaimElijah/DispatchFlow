package com.dispatchflow.planning.domain;

import static com.dispatchflow.planning.domain.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.PlanningFixtures.SECOND_JOB;
import static com.dispatchflow.planning.domain.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.PlanningFixtures.V3;
import static com.dispatchflow.planning.domain.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.PlanningFixtures.noChanges;
import static com.dispatchflow.planning.domain.PlanningFixtures.ordersOn;
import static com.dispatchflow.planning.domain.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.PlanningFixtures.roomIn;
import static com.dispatchflow.planning.domain.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.PlanningFixtures.routeOf;
import static com.dispatchflow.planning.domain.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.PlanningFixtures.succeededJob;
import static com.dispatchflow.planning.domain.PlanningFixtures.uuid;
import static com.dispatchflow.planning.domain.PlanningFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Stable route and stop identity, route indexes, and the facts a published plan keeps (ADR-101). */
class PlanRoutesAndFactsTest {

    // ---- a withdrawn vehicle the proposal gives no route ----------------------------------------

    @Test
    void
            GivenAProposalWithdrawingAVehicleItGivesNoRoute_WhenAccepted_ThenItsRouteStaysWithdrawnWithoutTheMovedStopsAndKeepsItsIndex() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        StopId o1 = stopId(plan, O1);
        StopId o2 = stopId(plan, O2);
        ReoptimizationProposal proposal = proposal(
                RevisionNo.FIRST,
                List.of(solved(V2, D2, O3, O1, O2), solved(V3, D3, O4)),
                new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE));

        plan.accept(proposal, noChanges(V2, V3), freshIds(700), DISPATCHER, TODAY, LATER);

        PlanSnapshot.RouteSnapshot withdrawn = route(plan, v1);
        assertThat(withdrawn.withdrawal()).contains(new Withdrawal(WithdrawalCause.VEHICLE_UNAVAILABLE));
        assertThat(withdrawn.stops()).isEmpty();
        assertThat(withdrawn.index()).isEqualTo(RouteIndex.of(1));
        assertThat(routeOf(plan, V2).index()).isEqualTo(RouteIndex.of(2));
        assertThat(routeOf(plan, V3).index()).isEqualTo(RouteIndex.of(3));
        assertThat(ordersOn(plan, V2)).containsExactly(O3, O1, O2);
        assertThat(stopId(plan, O1)).isEqualTo(o1);
        assertThat(stopId(plan, O2)).isEqualTo(o2);
        assertThatCode(() -> Plan.restore(plan.snapshot())).doesNotThrowAnyException();
    }

    @Test
    void
            GivenAnEarlierWithdrawnRoute_WhenAProposalGivesItsVehicleANewRoute_ThenTheWithdrawnRouteDoesNotCountUnderBr39() {
        Plan plan = Plan.restore(withRoutes(
                aPlan().published().snapshot(),
                route(1, V1, D1, false),
                route(2, V2, D2, false),
                route(3, V3, D3, true)));

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1), solved(V2, D2), solved(V3, D3, O4))),
                noChanges(V1, V2, V3),
                freshIds(700),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(routeOf(plan, V3).index()).isEqualTo(RouteIndex.of(4));
        assertThat(plan.snapshot().routes())
                .filteredOn(route -> route.vehicle().equals(V3))
                .hasSize(2);
    }

    @Test
    void
            GivenAVehicleWithdrawnEarlier_WhenASecondProposalWithdrawsItAgainWithTheSameStops_ThenItsRouteKeepsItsIdIndexAndStops() {
        Plan plan = withdrawnV1();
        PlanSnapshot.RouteSnapshot withdrawn = withdrawnRoute(plan);

        plan.accept(
                proposal(
                        RevisionNo.of(2),
                        List.of(solved(V1, D1, O1), solved(V2, D2, O3, O2)),
                        new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE)),
                noChanges(V2),
                freshIds(800),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(3)));
        PlanSnapshot.RouteSnapshot again = withdrawnRoute(plan);
        assertThat(again.id()).isEqualTo(withdrawn.id());
        assertThat(again.index()).isEqualTo(withdrawn.index());
        assertThat(again.stops())
                .extracting(PlanSnapshot.StopSnapshot::id)
                .containsExactlyElementsOf(withdrawn.stops().stream()
                        .map(PlanSnapshot.StopSnapshot::id)
                        .toList());
        assertThat(plan.snapshot().routes())
                .filteredOn(route -> route.vehicle().equals(V1))
                .hasSize(1);
    }

    @Test
    void GivenAWithdrawnRouteThatServedAnOrder_WhenAProposalServesThatOrderOnAnotherRoute_ThenOrderAlreadyInPlan() {
        Plan plan = withdrawnV1();

        PlanAssertions.assertPlanRefused(
                plan,
                () -> plan.accept(
                        proposal(RevisionNo.of(2), List.of(solved(V2, D2, O3, O2, O1))),
                        noChanges(V2),
                        freshIds(800),
                        DISPATCHER,
                        TODAY,
                        LATER),
                new PlanningError.OrderAlreadyInPlan(O1, FIRST));
    }

    // ---- route indexes --------------------------------------------------------------------------

    @Test
    void GivenTwentyNineIndexesTaken_WhenAProposalAddsARoute_ThenTheNewRouteTakesIndexThirty() {
        List<PlanSnapshot.RouteSnapshot> routes = new ArrayList<>();
        for (int n = 1; n <= 29; n++) {
            int owner = n <= 15 ? n : n - 15;
            routes.add(route(n, vehicle(owner), driver(owner), n <= 15));
        }
        Plan plan = Plan.restore(
                withRoutes(aPlan().published().snapshot(), routes.toArray(PlanSnapshot.RouteSnapshot[]::new)));
        List<SolvedRoute> proposed = new ArrayList<>(IntStream.rangeClosed(1, 14)
                .mapToObj(n -> solved(vehicle(n), driver(n)))
                .toList());
        proposed.add(solved(vehicle(16), driver(16)));
        VehicleId[] all = IntStream.rangeClosed(1, 16)
                .mapToObj(PlanRoutesAndFactsTest::vehicle)
                .toArray(VehicleId[]::new);

        plan.accept(proposal(RevisionNo.FIRST, proposed), noChanges(all), freshIds(700), DISPATCHER, TODAY, LATER);

        assertThat(routeOf(plan, vehicle(16)).index()).isEqualTo(RouteIndex.of(30));
    }

    @Test
    void GivenAGapInTheRouteIndexes_WhenAProposalAddsARoute_ThenTheNewRouteTakesTheLowestFreeIndex() {
        Plan plan = Plan.restore(
                withRoutes(aPlan().published().snapshot(), route(1, V1, D1, false), route(3, V2, D2, false)));

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1), solved(V2, D2), solved(V3, D3, O4))),
                noChanges(V1, V2, V3),
                freshIds(700),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(routeOf(plan, V3).index()).isEqualTo(RouteIndex.of(2));
    }

    // ---- late facts and pins --------------------------------------------------------------------

    @Test
    void
            GivenAFactWhoseStopIdDiffersButWhoseOrderIsOnAnotherRoute_WhenReconciled_ThenTheOrderMovesOnceAndKeepsItsStop() {
        Plan plan = aPlan().published();
        StopId o1 = stopId(plan, O1);
        LateFact fact = new LateFact(StopId.of(uuid(8_888)), O1, FIRST, routeId(plan, V2));

        Reconciliation result = plan.reconcileLateOutcome(fact, LATER);

        assertThat(result).isEqualTo(Reconciliation.movedFrom(routeId(plan, V1)));
        assertThat(ordersOn(plan, V2)).containsExactly(O3, O1);
        assertThat(ordersOn(plan, V1)).containsExactly(O2);
        assertThat(stopId(plan, O1)).isEqualTo(o1);
    }

    @Test
    void GivenADraft_WhenAPinIsJudgedWithAViolation_ThenThePinIsSetAndNoViolationIsKept() {
        Plan plan = aPlan().draft();
        Violation unservable = new Violation(
                RuleCode.of("FR-91"),
                Optional.empty(),
                Optional.of(O1),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        plan.pin(
                O1,
                new PinTarget.ToVehicle(V1),
                evaluationOf(plan).withViolation(unservable).build(),
                TODAY);

        assertThat(plan.snapshot().pins()).hasSize(1);
        assertThat(plan.snapshot().violations()).isEmpty();
    }

    // ---- confirmed returns, locks ---------------------------------------------------------------

    @Test
    void GivenAConfirmedReturn_WhenTheRouteIsHandedOverAgainUnchecked_ThenTheConfirmedReturnIsCleared() {
        Plan plan = confirmedHandOver();
        RouteId v1 = routeId(plan, V1);

        plan.handOver(v1, new DriverStanding(D1, true, true), Optional.empty(), false, DISPATCHER, TODAY, LATER);

        assertThat(routeOf(plan, V1).confirmedReturn()).isEmpty();
    }

    @Test
    void GivenAConfirmedReturn_WhenAProposalKeepsTheRoutesDriver_ThenTheConfirmedReturnStays() {
        Plan plan = confirmedHandOver();

        plan.accept(
                proposal(RevisionNo.of(2), List.of(solved(V1, D3, O1, O2), solved(V2, D2, O3))),
                noChanges(V1, V2),
                freshIds(700),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(routeOf(plan, V1).confirmedReturn()).contains(PlanningFixtures.at(70_000));
    }

    @Test
    void GivenALockedRouteOnAPublishedPlan_WhenThePlanIsCancelled_ThenItIsCancelled() {
        Plan ready = aPlan().ready();
        ready.lockRoute(routeId(ready, V1), RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = reload(ready);
        locked.publish(PlanningFixtures.checksFor(locked), DISPATCHER, TODAY, LATER);
        Plan plan = reload(locked);

        plan.cancel(StartedRoutes.none(), TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Cancelled(RevisionNo.FIRST, LATER));
        assertThat(routeOf(plan, V1).lock()).contains(RouteLock.WHOLE_ROUTE);
    }

    @Test
    void GivenAnHonouredWholeRouteLock_WhenTheNewResultIsApplied_ThenTheRouteKeepsItsIdItsStopsAndItsLock() {
        Plan ready = aPlan().ready();
        RouteId v2 = routeId(ready, V2);
        StopId o3 = stopId(ready, O3);
        ready.lockRoute(v2, RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = reload(ready);
        locked.requestSolve(
                SECOND_JOB,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(8),
                roomIn(Lane.DAILY),
                TODAY,
                LATER);
        Plan plan = reload(locked);
        ValidatedSolution honoured = ValidatedSolution.of(
                ValidationReport.of(List.of(), List.of(solved(V1, D1, O2, O1, O4), solved(V2, D2, O3)), List.of()));

        plan.applySolveResult(succeededJob(SECOND_JOB, honoured), freshIds(500), LATER);

        assertThat(routeId(plan, V2)).isEqualTo(v2);
        assertThat(stopId(plan, O3)).isEqualTo(o3);
        assertThat(routeOf(plan, V2).lock()).contains(RouteLock.WHOLE_ROUTE);
    }

    // ---- refusal order --------------------------------------------------------------------------

    @Test
    void GivenAResultThatChangesALockedRouteAndServesAnOrderTwice_WhenApplied_ThenTheLockIsRefusedFirstAsRouteLocked() {
        Plan ready = aPlan().ready();
        RouteId v2 = routeId(ready, V2);
        ready.lockRoute(v2, RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = reload(ready);
        locked.requestSolve(
                SECOND_JOB,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(8),
                roomIn(Lane.DAILY),
                TODAY,
                LATER);
        Plan plan = reload(locked);
        ValidatedSolution both = ValidatedSolution.of(
                ValidationReport.of(List.of(), List.of(solved(V1, D1, O1), solved(V2, D2, O1, O3)), List.of()));

        PlanAssertions.assertPlanRefused(
                plan,
                () -> plan.applySolveResult(succeededJob(SECOND_JOB, both), freshIds(500), LATER),
                new PlanningError.RouteLocked(v2));
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** A published plan whose V1 was withdrawn after serving O1, with O2 moved to V2: revision 2. */
    private static Plan withdrawnV1() {
        Plan plan = aPlan().published();
        plan.accept(
                proposal(
                        RevisionNo.FIRST,
                        List.of(solved(V1, D1, O1), solved(V2, D2, O3, O2)),
                        new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE)),
                noChanges(V2),
                freshIds(700),
                DISPATCHER,
                TODAY,
                LATER);
        return reload(plan);
    }

    private static PlanSnapshot.RouteSnapshot withdrawnRoute(Plan plan) {
        return plan.snapshot().routes().stream()
                .filter(route -> route.withdrawal().isPresent())
                .findFirst()
                .orElseThrow();
    }

    private static Plan confirmedHandOver() {
        Plan published = aPlan().published();
        RouteId v1 = routeId(published, V1);
        Evaluation breaks = evaluationOf(published)
                .handingOver(v1, D3)
                .withViolation(violation("BR-06", v1))
                .returningAt(70_000)
                .build();
        published.handOver(v1, new DriverStanding(D3, true, true), Optional.of(breaks), true, DISPATCHER, TODAY, LATER);
        return reload(published);
    }

    private static ReoptimizationProposal proposal(
            RevisionNo basis, List<SolvedRoute> routes, VehicleWithdrawal... withdrawn) {
        SolveJob job = SolveJob.queue(
                JobId.of(uuid(13)),
                PLAN,
                Lane.REOPTIMIZATION,
                SolverName.OR_TOOLS,
                SolveLimit.REOPTIMIZATION,
                Seed.of(1),
                basis,
                roomIn(Lane.REOPTIMIZATION),
                NOW);
        job.claim(NOW);
        job.searchStarted(NOW);
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(List.of(), routes, List.of()));
        return job.succeed(PROPOSAL, reoptimizationResult(solution, withdrawn), LATER);
    }

    private static PlanSnapshot.RouteSnapshot route(Plan plan, RouteId id) {
        return plan.snapshot().routes().stream()
                .filter(route -> route.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static PlanSnapshot.RouteSnapshot route(int index, VehicleId vehicle, AccountId driver, boolean withdrawn) {
        return new PlanSnapshot.RouteSnapshot(
                RouteId.of(uuid(7_000 + index)),
                RouteIndex.of(index),
                vehicle,
                driver,
                Optional.empty(),
                CheckState.CHECKED,
                withdrawn ? Optional.of(new Withdrawal(WithdrawalCause.SHIFT_RAN_OUT)) : Optional.empty(),
                Optional.empty(),
                PlanningFixtures.routePlan(50_000),
                List.of());
    }

    private static PlanSnapshot withRoutes(PlanSnapshot published, PlanSnapshot.RouteSnapshot... routes) {
        return new PlanSnapshot(
                published.id(),
                published.version(),
                published.date(),
                published.openedAt(),
                published.status(),
                published.applied(),
                List.of(routes),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static VehicleId vehicle(int n) {
        return VehicleId.of(uuid(5_000 + n));
    }

    private static AccountId driver(int n) {
        return AccountId.of(uuid(6_000 + n));
    }
}
