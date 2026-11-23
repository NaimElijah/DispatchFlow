package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.SECOND_JOB;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.noChanges;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.ordersOn;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.roomIn;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.succeededJob;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.uuid;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.basis.ChangesSinceBasis;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.reconciliation.LateFact;
import com.dispatchflow.planning.domain.reconciliation.Reconciliation;
import com.dispatchflow.planning.domain.reconciliation.ReconciliationKind;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.SolvedStop;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.domain.solution.Withdrawal;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
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

    /** The published plan's drops, which a proposal that serves neither order drops again. */
    private static final List<DroppedOrder> BOTH_DROPS =
            List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation()));

    private static final List<DroppedOrder> O5_DROPPED = List.of(dropped(O5, new DropReason.MissingLocation()));

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
                O5_DROPPED,
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
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1), solved(V2, D2), solved(V3, D3, O4)), List.of()),
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
            GivenAVehicleWithdrawnEarlier_WhenASecondProposalWithdrawsItAgainWithNoStops_ThenItsRouteKeepsItsIdIndexAndStops() {
        Plan plan = withdrawnV1();
        PlanSnapshot.RouteSnapshot withdrawn = withdrawnRoute(plan);

        plan.accept(
                proposal(
                        RevisionNo.of(2),
                        List.of(solved(V1, D1), solved(V2, D2, O3, O2)),
                        BOTH_DROPS,
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
        assertThat(again.stops()).extracting(PlanSnapshot.StopSnapshot::order).containsExactly(O1);
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
                        proposal(RevisionNo.of(2), List.of(solved(V2, D2, O3, O2, O1)), BOTH_DROPS),
                        noChanges(V2),
                        freshIds(800),
                        DISPATCHER,
                        TODAY,
                        LATER),
                new PlanningError.OrderAlreadyInPlan(O1, FIRST));
    }

    // ---- accepting keeps what the proposal leaves out --------------------------------------------

    @Test
    void GivenAStopReportedBeforeTheBasis_WhenAProposalLeavesItOnNoRoute_ThenAcceptKeepsItOnItsRoute() {
        Plan plan = aPlan().published();
        StopId o1 = stopId(plan, O1);

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1, O2), solved(V2, D2, O3)), BOTH_DROPS),
                noChanges(V1, V2),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(ordersOn(plan, V1)).containsExactly(O1, O2);
        assertThat(stopId(plan, O1)).isEqualTo(o1);
        assertThatCode(() -> Plan.restore(plan.snapshot())).doesNotThrowAnyException();
    }

    @Test
    void GivenAVehicleWithdrawnAfterAStop_WhenTheProposalGivesItARouteWithNoStops_ThenItsRouteKeepsTheStopItMade() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        StopId o1 = stopId(plan, O1);

        plan.accept(
                proposal(
                        RevisionNo.FIRST,
                        List.of(solved(V1, D1), solved(V2, D2, O2, O3)),
                        BOTH_DROPS,
                        new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE)),
                noChanges(V2),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        PlanSnapshot.RouteSnapshot withdrawn = route(plan, v1);
        assertThat(withdrawn.withdrawal()).contains(new Withdrawal(WithdrawalCause.VEHICLE_UNAVAILABLE));
        assertThat(withdrawn.stops()).extracting(PlanSnapshot.StopSnapshot::id).containsExactly(o1);
        assertThat(ordersOn(plan, V2)).containsExactly(O2, O3);
    }

    @Test
    void GivenAStopReportedSinceTheBasisThatTheProposalKeepsAfterTheReportedOne_WhenAccepted_ThenTheReportStands() {
        Plan plan = aPlan().published();
        ChangesSinceBasis reportedO2 = new ChangesSinceBasis(
                List.of(stopId(plan, O2)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                noChanges(V1, V2).standing());

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1, O2), solved(V2, D2, O3)), BOTH_DROPS),
                reportedO2,
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(2)));
        assertThat(ordersOn(plan, V1)).containsExactly(O1, O2);
    }

    @Test
    void GivenARouteTheProposalLeavesOutWithoutWithdrawingIt_WhenAccepted_ThenItStaysInForceForALateFact() {
        Plan plan = aPlan().published();
        RouteId v2 = routeId(plan, V2);
        StopId o3 = stopId(plan, O3);
        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1, O1, O2, O3)), BOTH_DROPS),
                noChanges(V1),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);
        Plan accepted = reload(plan);

        Reconciliation reconciled =
                accepted.reconcileLateOutcome(new LateFact(o3, O3, FIRST, v2), LATER.plusSeconds(120));

        assertThat(reconciled.kind()).isEqualTo(ReconciliationKind.MOVED_FROM_ROUTE);
        assertThat(ordersOn(accepted, V2)).containsExactly(O3);
        assertThat(ordersOn(accepted, V1)).containsExactly(O1, O2);
    }

    @Test
    void GivenAProposalThatNeitherServesNorDropsADroppedOrder_WhenAccepted_ThenTheDropIsCarriedForwardAsItWas() {
        Plan plan = aPlan().published();

        plan.accept(
                proposal(
                        RevisionNo.FIRST,
                        List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)),
                        List.of(BOTH_DROPS.get(0))),
                noChanges(V1, V2),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(2)));
        assertThat(plan.snapshot().dropped()).containsExactlyElementsOf(BOTH_DROPS);
        assertThatCode(() -> Plan.restore(plan.snapshot())).doesNotThrowAnyException();
    }

    @Test
    void GivenADropPutBackOntoTheSameDate_WhenAProposalServesItsNextAttempt_ThenTheOldDropLeavesAndTheNewStopStays() {
        Plan plan = aPlan().published();
        Attempt second = FIRST.next();
        SolvedRoute v2 = new SolvedRoute(
                V2,
                D2,
                PlanningFixtures.routePlan(50_000),
                List.of(
                        new SolvedStop(O3, FIRST, PlanningFixtures.stopPlan(28_800)),
                        new SolvedStop(O4, second, PlanningFixtures.stopPlan(30_000))));

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1, O1, O2), v2), O5_DROPPED),
                noChanges(V1, V2),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(2)));
        assertThat(routeOf(plan, V2).stops())
                .extracting(PlanSnapshot.StopSnapshot::order, PlanSnapshot.StopSnapshot::attempt)
                .containsExactly(tuple(O3, FIRST), tuple(O4, second));
        assertThat(plan.snapshot().dropped()).containsExactlyElementsOf(O5_DROPPED);
        assertThat(((PlanRevised) plan.pendingEvents().get(0)).addedOrders()).isEmpty();
        assertThatCode(() -> Plan.restore(plan.snapshot())).doesNotThrowAnyException();
    }

    @Test
    void GivenADropPutBackOntoTheSameDate_WhenAProposalDropsItsNextAttempt_ThenOnlyTheNewDropStays() {
        Plan plan = aPlan().published();
        DroppedOrder secondDrop = new DroppedOrder(O4, FIRST.next(), new DropReason.Displaced());

        plan.accept(
                proposal(
                        RevisionNo.FIRST,
                        List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)),
                        List.of(secondDrop, O5_DROPPED.get(0))),
                noChanges(V1, V2),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(plan.snapshot().dropped()).containsExactly(secondDrop, O5_DROPPED.get(0));
    }

    @Test
    void GivenADropPutBackOntoAnotherDate_WhenEachLaterProposalLeavesItOut_ThenEveryAcceptanceKeepsItAsHistory() {
        Plan plan = aPlan().published();
        plan.removePutBackOrder(O4, FIRST);
        ReoptimizationProposal first =
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)), O5_DROPPED);
        plan.accept(first, noChanges(V1, V2), freshIds(500), DISPATCHER, TODAY, LATER);
        Plan revised = reload(plan);

        revised.accept(
                proposal(RevisionNo.of(2), List.of(solved(V1, D1, O2, O1), solved(V2, D2, O3)), O5_DROPPED),
                noChanges(V1, V2),
                freshIds(700),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(revised.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(3)));
        assertThat(revised.snapshot().dropped()).containsExactlyElementsOf(BOTH_DROPS);
        assertThat(ordersOn(revised, V1)).containsExactly(O2, O1);
    }

    @Test
    void GivenARouteTheProposalLeavesOutAndEmpties_WhenAccepted_ThenItHasNoPlanAndIsNotYetChecked() {
        Plan plan = aPlan().published();
        RouteId v2 = routeId(plan, V2);

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1, O1, O2, O3)), BOTH_DROPS),
                noChanges(V1),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        PlanSnapshot.RouteSnapshot emptied = route(plan, v2);
        assertThat(emptied.stops()).isEmpty();
        assertThat(emptied.plan()).isEmpty();
        assertThat(emptied.check()).isEqualTo(CheckState.NOT_YET_CHECKED);
    }

    @Test
    void GivenARouteTheProposalLeavesOutAndTakesOneStopFrom_WhenAccepted_ThenItKeepsItsPlanAndIsNotYetChecked() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        PlanSnapshot.RouteSnapshot before = route(plan, v1);

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V2, D2, O3, O2)), BOTH_DROPS),
                noChanges(V2),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        PlanSnapshot.RouteSnapshot kept = route(plan, v1);
        assertThat(kept.stops()).extracting(PlanSnapshot.StopSnapshot::order).containsExactly(O1);
        assertThat(kept.plan()).isEqualTo(before.plan());
        assertThat(kept.check()).isEqualTo(CheckState.NOT_YET_CHECKED);
    }

    @Test
    void GivenARouteTheProposalLeavesOutAndTakesNoStopFrom_WhenAccepted_ThenItStaysAsItWas() {
        Plan plan = aPlan().published();
        RouteId v2 = routeId(plan, V2);
        PlanSnapshot.RouteSnapshot before = route(plan, v2);

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1, O1, O2)), BOTH_DROPS),
                noChanges(V1),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(route(plan, v2)).isEqualTo(before);
    }

    @Test
    void GivenAProposalListingTheRoutesInAnotherOrder_WhenAccepted_ThenThePlanKeepsItsRouteOrder() {
        Plan plan = aPlan().published();
        List<RouteId> before = routeIds(plan);

        plan.accept(
                proposal(
                        RevisionNo.FIRST,
                        List.of(solved(V3, D3, O4), solved(V2, D2, O3, O2), solved(V1, D1, O1)),
                        O5_DROPPED),
                noChanges(V1, V2, V3),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(routeIds(plan)).startsWith(before.toArray(RouteId[]::new));
        assertThat(routeIds(plan).get(2)).isEqualTo(routeId(plan, V3));
    }

    @Test
    void GivenAProposalWithdrawingTheFirstRouteWithNoRouteForIt_WhenAccepted_ThenTheWithdrawnRouteKeepsItsPlace() {
        Plan plan = aPlan().published();
        List<RouteId> before = routeIds(plan);

        plan.accept(
                proposal(
                        RevisionNo.FIRST,
                        List.of(solved(V2, D2, O3, O1, O2)),
                        BOTH_DROPS,
                        new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE)),
                noChanges(V2),
                freshIds(500),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(routeIds(plan)).isEqualTo(before);
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

        plan.accept(
                proposal(RevisionNo.FIRST, proposed, List.of()),
                noChanges(all),
                freshIds(700),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(routeOf(plan, vehicle(16)).index()).isEqualTo(RouteIndex.of(30));
    }

    @Test
    void GivenAGapInTheRouteIndexes_WhenAProposalAddsARoute_ThenTheNewRouteTakesTheLowestFreeIndex() {
        Plan plan = Plan.restore(
                withRoutes(aPlan().published().snapshot(), route(1, V1, D1, false), route(3, V2, D2, false)));

        plan.accept(
                proposal(RevisionNo.FIRST, List.of(solved(V1, D1), solved(V2, D2), solved(V3, D3, O4)), List.of()),
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
                proposal(RevisionNo.of(2), List.of(solved(V1, D3, O1, O2), solved(V2, D2, O3)), BOTH_DROPS),
                noChanges(V1, V2),
                freshIds(700),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(routeOf(plan, V1).confirmedReturn()).contains(PlanningFixtures.at(70_000));
    }

    @Test
    void GivenARouteLockedBeforePublishing_WhenThePlanIsPublishedThenCancelled_ThenItIsCancelledWithNoLock() {
        Plan ready = aPlan().ready();
        ready.lockRoute(routeId(ready, V1), RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = reload(ready);
        locked.publish(PlanningFixtures.checksFor(locked), DISPATCHER, TODAY, LATER);
        Plan plan = reload(locked);

        plan.cancel(StartedRoutes.none(), TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Cancelled(RevisionNo.FIRST, LATER));
        assertThat(routeOf(plan, V1).lock()).isEmpty();
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
        ValidatedSolution honoured = ValidatedSolution.of(ValidationReport.of(
                List.of(), List.of(solved(V1, D1, O2, O1, O4), solved(V2, D2, O3)), List.of(), List.of()));

        plan.applySolveResult(succeededJob(SECOND_JOB, honoured), freshIds(500), LATER);

        assertThat(routeId(plan, V2)).isEqualTo(v2);
        assertThat(stopId(plan, O3)).isEqualTo(o3);
        assertThat(routeOf(plan, V2).lock()).contains(RouteLock.WHOLE_ROUTE);
    }

    // ---- helpers --------------------------------------------------------------------------------

    /**
     * A published plan whose V1 was withdrawn after serving O1, with O2 moved to V2: revision 2. The
     * proposal gives V1 a route with no stops, as the optimizer does: a delivered stop is on no route
     * of a proposal (BR-10), and no stop is added to a withdrawn route (BR-108).
     */
    private static Plan withdrawnV1() {
        Plan plan = aPlan().published();
        plan.accept(
                proposal(
                        RevisionNo.FIRST,
                        List.of(solved(V1, D1), solved(V2, D2, O3, O2)),
                        BOTH_DROPS,
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
            RevisionNo basis, List<SolvedRoute> routes, List<DroppedOrder> drops, VehicleWithdrawal... withdrawn) {
        SolveJob job = SolveJob.queue(
                JobId.of(uuid(13)),
                PLAN,
                Lane.REOPTIMIZATION,
                SolverName.OR_TOOLS,
                SolveLimit.REOPTIMIZATION,
                Seed.of(1),
                Optional.of(basis),
                roomIn(Lane.REOPTIMIZATION),
                NOW);
        job.claim(NOW);
        job.searchStarted(NOW);
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, List.of()));
        return job.succeed(PROPOSAL, reoptimizationResult(solution, withdrawn), LATER);
    }

    private static List<RouteId> routeIds(Plan plan) {
        return plan.snapshot().routes().stream()
                .map(PlanSnapshot.RouteSnapshot::id)
                .toList();
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
