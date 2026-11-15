package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertPlanRefused;
import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertUnchanged;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DATE;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O6;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.OTHER_PLAN;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TOMORROW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aJob;
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
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.uuid;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.BasisChange;
import com.dispatchflow.planning.domain.PlanningError.BreakNotConfirmed;
import com.dispatchflow.planning.domain.PlanningError.DateInPast;
import com.dispatchflow.planning.domain.PlanningError.DateNotOver;
import com.dispatchflow.planning.domain.PlanningError.DriverAlreadyHasRoute;
import com.dispatchflow.planning.domain.PlanningError.DriverNotAvailable;
import com.dispatchflow.planning.domain.PlanningError.NotAnActiveDriver;
import com.dispatchflow.planning.domain.PlanningError.PlanNotDraftSolvingOrReady;
import com.dispatchflow.planning.domain.PlanningError.PlanNotPublished;
import com.dispatchflow.planning.domain.PlanningError.ProposalDeclined;
import com.dispatchflow.planning.domain.PlanningError.ProposalForAnotherPlan;
import com.dispatchflow.planning.domain.PlanningError.ProposalOutdated;
import com.dispatchflow.planning.domain.PlanningError.RouteAlreadyStarted;
import com.dispatchflow.planning.domain.PlanningError.RouteIndexesUsedUp;
import com.dispatchflow.planning.domain.PlanningError.RouteNotInPlan;
import com.dispatchflow.planning.domain.PlanningError.RouteWithdrawn;
import com.dispatchflow.planning.domain.PlanningError.StaleEvaluation;
import com.dispatchflow.planning.domain.PlanningError.StopAlreadyReported;
import com.dispatchflow.planning.domain.PlanningError.VehicleUnavailable;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.basis.ChangesSinceBasis;
import com.dispatchflow.planning.domain.basis.VehicleStanding;
import com.dispatchflow.planning.domain.reconciliation.LateFact;
import com.dispatchflow.planning.domain.reconciliation.LateOutcome;
import com.dispatchflow.planning.domain.reconciliation.Reconciliation;
import com.dispatchflow.planning.domain.reconciliation.ReconciliationKind;
import com.dispatchflow.planning.domain.revision.PlanRevision;
import com.dispatchflow.planning.domain.solution.DropReason;
import com.dispatchflow.planning.domain.solution.DroppedOrder;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.domain.solution.Withdrawal;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.PlanCancelled;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.planning.events.RevisionCause;
import com.dispatchflow.planning.events.RouteHandedOver;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlanPublishedTest {

    private static final DriverStanding D3_STANDS = new DriverStanding(D3, true, true);

    // ---- accept ---------------------------------------------------------------------------------

    @Test
    void GivenAProposalThatMovesAStopAndAddsARoute_WhenAccepted_ThenItIsTheNextRevisionWithStableIds() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        RouteId v2 = routeId(plan, V2);
        StopId o2 = stopId(plan, O2);
        ReoptimizationProposal proposal = proposal(
                List.of(solved(V1, D1, O1), solved(V2, D2, O3, O2), solved(V3, D3, O4)),
                List.of(dropped(O5, new DropReason.MissingLocation())));

        plan.accept(proposal, noChanges(V1, V2, V3), freshIds(700), DISPATCHER, TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(2)));
        assertThat(routeId(plan, V1)).isEqualTo(v1);
        assertThat(stopId(plan, O2)).isEqualTo(o2);
        assertThat(ordersOn(plan, V2)).containsExactly(O3, O2);
        assertThat(routeOf(plan, V3).index()).isEqualTo(RouteIndex.of(3));
        RouteId v3 = routeId(plan, V3);
        assertThat(plan.pendingEvents())
                .containsExactly(new PlanRevised(
                        new EventId("PlanRevised", PLAN.value(), plan.version().next(), 0),
                        LATER,
                        PLAN,
                        DATE,
                        2,
                        RevisionCause.REOPTIMIZATION_ACCEPTED,
                        List.of(v1, v2, v3),
                        List.of(),
                        List.of(),
                        List.of()));
        PlanRevision revision = plan.pendingRevision().orElseThrow();
        assertThat(revision.proposal()).contains(PROPOSAL);
        assertThat(revision.by()).contains(DISPATCHER);
    }

    @Test
    void GivenAProposalThatWithdrawsAVehicle_WhenAccepted_ThenItsRouteKeepsTheStopsItMadeAndHoldsTheCause() {
        Plan plan = aPlan().published();
        ReoptimizationProposal proposal = proposal(
                List.of(solved(V1, D1), solved(V2, D2, O3, O2)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE));

        plan.accept(proposal, noChanges(V2), freshIds(700), DISPATCHER, TODAY, LATER);

        PlanSnapshot.RouteSnapshot withdrawn = plan.snapshot().routes().stream()
                .filter(route -> route.vehicle().equals(V1))
                .findFirst()
                .orElseThrow();
        assertThat(withdrawn.withdrawal()).contains(new Withdrawal(WithdrawalCause.VEHICLE_UNAVAILABLE));
        assertThat(withdrawn.stops())
                .extracting(PlanSnapshot.StopSnapshot::order)
                .containsExactly(O1);
        assertThat(((PlanRevised) plan.pendingEvents().get(0)).withdrawnVehicles())
                .containsExactly(V1);
    }

    @Test
    void GivenAReadyPlan_WhenAProposalIsAccepted_ThenPlanNotPublished() {
        Plan plan = aPlan().ready();

        assertPlanRefused(
                plan,
                () -> plan.accept(standardProposal(), noChanges(V1, V2), freshIds(700), DISPATCHER, TODAY, LATER),
                new PlanNotPublished(PLAN));
    }

    @Test
    void GivenAProposalForAnotherPlan_WhenAccepted_ThenProposalForAnotherPlan() {
        Plan plan = aPlan().published();
        SolveJob job = SolveJob.queue(
                JobId.of(uuid(12)),
                OTHER_PLAN,
                Lane.REOPTIMIZATION,
                SolverName.OR_TOOLS,
                SolveLimit.REOPTIMIZATION,
                Seed.of(1),
                Optional.of(RevisionNo.FIRST),
                roomIn(Lane.REOPTIMIZATION),
                NOW);
        job.claim(NOW);
        job.searchStarted(NOW);
        ReoptimizationProposal elsewhere =
                job.succeed(PROPOSAL, reoptimizationResult(PlanningFixtures.standardSolution()), LATER);

        assertPlanRefused(
                plan,
                () -> plan.accept(elsewhere, noChanges(V1, V2), freshIds(700), DISPATCHER, TODAY, LATER),
                new ProposalForAnotherPlan(PROPOSAL, OTHER_PLAN));
    }

    @Test
    void GivenADeclinedProposal_WhenAccepted_ThenProposalDeclined() {
        Plan plan = aPlan().published();
        ReoptimizationProposal declined = standardProposal();
        declined.decline(DISPATCHER, LATER);

        assertPlanRefused(
                plan,
                () -> plan.accept(declined, noChanges(V1, V2), freshIds(700), DISPATCHER, TODAY, LATER),
                new ProposalDeclined(PROPOSAL));
    }

    @Test
    void GivenARevisionSinceTheBasis_WhenAccepted_ThenProposalOutdated() {
        Plan published = aPlan().published();
        published.handOver(routeId(published, V1), D3_STANDS, Optional.empty(), false, DISPATCHER, TODAY, LATER);
        Plan plan = reload(published);

        assertPlanRefused(
                plan,
                () -> plan.accept(standardProposal(), noChanges(V1, V2), freshIds(700), DISPATCHER, TODAY, LATER),
                new ProposalOutdated(PROPOSAL, List.of(new BasisChange.RevisionMade(RevisionNo.of(2)))));
    }

    @Test
    void GivenAReportOnAStopTheProposalMoves_WhenAccepted_ThenProposalOutdatedNamesTheStop() {
        Plan plan = aPlan().published();
        StopId o1 = stopId(plan, O1);
        ReoptimizationProposal movesO1 = proposal(List.of(solved(V1, D1, O2), solved(V2, D2, O1, O3)), List.of());
        ChangesSinceBasis reported = new ChangesSinceBasis(
                List.of(o1),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                noChanges(V1, V2).standing());

        assertPlanRefused(
                plan,
                () -> plan.accept(movesO1, reported, freshIds(700), DISPATCHER, TODAY, LATER),
                new ProposalOutdated(PROPOSAL, List.of(new BasisChange.ReportedStop(o1))));
    }

    @Test
    void GivenAReportOnAStopTheProposalLeavesInPlace_WhenAccepted_ThenTheReportStandsAndItIsAccepted() {
        Plan plan = aPlan().published();
        StopId o1 = stopId(plan, O1);
        ChangesSinceBasis reported = new ChangesSinceBasis(
                List.of(o1),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                noChanges(V1, V2).standing());

        plan.accept(standardProposal(), reported, freshIds(700), DISPATCHER, TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(2)));
        assertThat(stopId(plan, O1)).isEqualTo(o1);
    }

    @Test
    void GivenChangesToWhatTheProposalReliesOn_WhenAccepted_ThenProposalOutdatedNamesEachInOrder() {
        Plan plan = aPlan().published();
        ChangesSinceBasis changes = new ChangesSinceBasis(
                List.of(),
                List.of(O1, O6),
                List.of(O3),
                List.of(V2, V3),
                List.of(D1),
                noChanges(V1, V2).standing());

        assertPlanRefused(
                plan,
                () -> plan.accept(standardProposal(), changes, freshIds(700), DISPATCHER, TODAY, LATER),
                new ProposalOutdated(
                        PROPOSAL,
                        List.of(
                                new BasisChange.CancelledOrder(O1),
                                new BasisChange.ChangedOrder(O3),
                                new BasisChange.ChangedVehicle(V2),
                                new BasisChange.ChangedDriver(D1))));
    }

    @Test
    void GivenVehiclesThatNoLongerMeetBr20_WhenAccepted_ThenVehicleUnavailable() {
        Plan plan = aPlan().published();
        ChangesSinceBasis changes = new ChangesSinceBasis(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(new VehicleStanding(V1, false)));

        assertPlanRefused(
                plan,
                () -> plan.accept(standardProposal(), changes, freshIds(700), DISPATCHER, TODAY, LATER),
                new VehicleUnavailable(List.of(V1, V2)));
    }

    @Test
    void GivenEveryRouteIndexTaken_WhenAProposalAddsARoute_ThenRouteIndexesUsedUp() {
        Plan plan = Plan.restore(withThirtyRoutes(aPlan().published().snapshot()));
        ReoptimizationProposal addsARoute = proposal(
                IntStream.rangeClosed(1, 16)
                        .mapToObj(n -> solved(vehicle(n), driver(n)))
                        .toList(),
                List.of());
        VehicleId[] all = IntStream.rangeClosed(1, 16)
                .mapToObj(PlanPublishedTest::vehicle)
                .toArray(VehicleId[]::new);

        assertPlanRefused(
                plan,
                () -> plan.accept(addsARoute, noChanges(all), freshIds(700), DISPATCHER, TODAY, LATER),
                new RouteIndexesUsedUp());
    }

    // ---- hand-over ------------------------------------------------------------------------------

    @Test
    void GivenNoEvaluation_WhenARouteIsHandedOver_ThenItHasTheNewDriverIsNotYetCheckedAndBothEventsAreRaised() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);

        plan.handOver(v1, D3_STANDS, Optional.empty(), false, DISPATCHER, TODAY, LATER);

        assertThat(routeOf(plan, V1).driver()).isEqualTo(D3);
        assertThat(routeOf(plan, V1).check()).isEqualTo(CheckState.NOT_YET_CHECKED);
        Version saved = plan.version().next();
        assertThat(plan.pendingEvents())
                .containsExactly(
                        new PlanRevised(
                                new EventId("PlanRevised", PLAN.value(), saved, 0),
                                LATER,
                                PLAN,
                                DATE,
                                2,
                                RevisionCause.HANDED_OVER,
                                List.of(v1),
                                List.of(),
                                List.of(),
                                List.of()),
                        new RouteHandedOver(
                                new EventId("RouteHandedOver", PLAN.value(), saved, 1),
                                LATER,
                                PLAN,
                                DATE,
                                v1,
                                V1,
                                D1,
                                D3));
    }

    @Test
    void GivenAnEvaluationWithoutABrokenShift_WhenARouteIsHandedOver_ThenItIsChecked() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);

        plan.handOver(
                v1,
                D3_STANDS,
                Optional.of(evaluationOf(plan).handingOver(v1, D3).build()),
                false,
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(routeOf(plan, V1).check()).isEqualTo(CheckState.CHECKED);
        assertThat(routeOf(plan, V1).confirmedReturn()).isEmpty();
    }

    @Test
    void GivenTheOptimizerIsDown_WhenARouteIsHandedOver_ThenItIsNotYetChecked() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);

        plan.handOver(v1, D3_STANDS, Optional.of(evaluationOf(plan).unavailable()), false, DISPATCHER, TODAY, LATER);

        assertThat(routeOf(plan, V1).check()).isEqualTo(CheckState.NOT_YET_CHECKED);
    }

    @Test
    void GivenABrokenShiftNotConfirmed_WhenARouteIsHandedOver_ThenBreakNotConfirmed() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        Evaluation breaks = evaluationOf(plan)
                .handingOver(v1, D3)
                .withViolation(violation("BR-06", v1))
                .build();

        assertPlanRefused(
                plan,
                () -> plan.handOver(v1, D3_STANDS, Optional.of(breaks), false, DISPATCHER, TODAY, LATER),
                new BreakNotConfirmed(v1, D3));
    }

    @Test
    void GivenABreakReserveViolationOnly_WhenARouteIsHandedOverUnconfirmed_ThenItIsHandedOver() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        Evaluation reserve = evaluationOf(plan)
                .handingOver(v1, D3)
                .withViolation(violation("BR-105", v1))
                .build();

        plan.handOver(v1, D3_STANDS, Optional.of(reserve), false, DISPATCHER, TODAY, LATER);

        assertThat(routeOf(plan, V1).driver()).isEqualTo(D3);
        assertThat(plan.snapshot().violations()).containsExactly(violation("BR-105", v1));
    }

    @Test
    void GivenABrokenShiftAndBreakReserveConfirmed_WhenARouteIsHandedOver_ThenBothAreWaivedAndOthersStayOpen() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        RouteId v2 = routeId(plan, V2);
        Evaluation breaks = evaluationOf(plan)
                .handingOver(v1, D3)
                .withViolation(violation("BR-06", v1))
                .withViolation(violation("BR-105", v1))
                .withViolation(violation("BR-105", v2))
                .withViolation(violation("BR-03", v1))
                .returningAt(70_000)
                .build();

        plan.handOver(v1, D3_STANDS, Optional.of(breaks), true, DISPATCHER, TODAY, LATER);

        assertThat(plan.snapshot().violations()).containsExactly(violation("BR-105", v2), violation("BR-03", v1));
    }

    @Test
    void GivenABrokenShiftConfirmed_WhenARouteIsHandedOver_ThenTheConfirmedReturnIsTheEvaluatedReturn() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        Evaluation breaks = evaluationOf(plan)
                .handingOver(v1, D3)
                .withViolation(violation("BR-06", v1))
                .returningAt(70_000)
                .build();

        plan.handOver(v1, D3_STANDS, Optional.of(breaks), true, DISPATCHER, TODAY, LATER);

        assertThat(routeOf(plan, V1).confirmedReturn()).contains(PlanningFixtures.at(70_000));
        assertThat(plan.snapshot().violations()).isEmpty();
    }

    @Test
    void GivenAnInactiveAccount_WhenARouteIsHandedOverToIt_ThenNotAnActiveDriver() {
        Plan plan = aPlan().published();

        assertPlanRefused(
                plan,
                () -> plan.handOver(
                        routeId(plan, V1),
                        new DriverStanding(D3, false, true),
                        Optional.empty(),
                        false,
                        DISPATCHER,
                        TODAY,
                        LATER),
                new NotAnActiveDriver(D3));
    }

    @Test
    void GivenAnUnavailableDriver_WhenARouteIsHandedOverToThem_ThenDriverNotAvailable() {
        Plan plan = aPlan().published();

        assertPlanRefused(
                plan,
                () -> plan.handOver(
                        routeId(plan, V1),
                        new DriverStanding(D3, true, false),
                        Optional.empty(),
                        false,
                        DISPATCHER,
                        TODAY,
                        LATER),
                new DriverNotAvailable(D3));
    }

    @Test
    void GivenADriverWithARoute_WhenAnotherRouteIsHandedOverToThem_ThenDriverAlreadyHasRoute() {
        Plan plan = aPlan().published();
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.handOver(
                        routeId(plan, V1),
                        new DriverStanding(D2, true, true),
                        Optional.empty(),
                        false,
                        DISPATCHER,
                        TODAY,
                        LATER),
                new DriverAlreadyHasRoute(D2, v2));
    }

    @Test
    void GivenAWithdrawnRoute_WhenItIsHandedOver_ThenRouteWithdrawn() {
        Plan accepted = aPlan().published();
        accepted.accept(
                proposal(
                        List.of(solved(V1, D1), solved(V2, D2, O3, O2)),
                        List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                        new VehicleWithdrawal(V1, WithdrawalCause.NO_DRIVER)),
                noChanges(V2),
                freshIds(700),
                DISPATCHER,
                TODAY,
                LATER);
        Plan plan = reload(accepted);
        RouteId withdrawn = plan.snapshot().routes().stream()
                .filter(route -> route.withdrawal().isPresent())
                .findFirst()
                .orElseThrow()
                .id();

        assertPlanRefused(
                plan,
                () -> plan.handOver(withdrawn, D3_STANDS, Optional.empty(), false, DISPATCHER, TODAY, LATER),
                new RouteWithdrawn(withdrawn));
    }

    @Test
    void GivenAnUnknownRoute_WhenItIsHandedOver_ThenRouteNotInPlan() {
        Plan plan = aPlan().published();
        RouteId unknown = RouteId.of(uuid(999));

        assertPlanRefused(
                plan,
                () -> plan.handOver(unknown, D3_STANDS, Optional.empty(), false, DISPATCHER, TODAY, LATER),
                new RouteNotInPlan(unknown));
    }

    @Test
    void GivenAnEvaluationOfAnotherVersion_WhenARouteIsHandedOver_ThenStaleEvaluation() {
        Plan plan = aPlan().published();
        Evaluation old = Evaluation.checked(Version.NEW, List.of(), List.of());

        assertPlanRefused(
                plan,
                () -> plan.handOver(routeId(plan, V1), D3_STANDS, Optional.of(old), false, DISPATCHER, TODAY, LATER),
                new StaleEvaluation(Version.NEW, plan.version()));
    }

    // ---- removeStopOf ---------------------------------------------------------------------------

    @Test
    void GivenAReadyPlan_WhenACancelledOrdersStopIsRemoved_ThenItGoesAtOnceItsRouteIsUncheckedAndTheJournalCleared() {
        Plan plan = aPlan().ready();
        plan.lockRoute(routeId(plan, V1), RouteLock.WHOLE_ROUTE, TODAY);

        plan.removeStopOf(O1, FIRST, RemovalCause.ORDER_CANCELLED, LATER);

        assertThat(ordersOn(plan, V1)).containsExactly(O2);
        assertThat(routeOf(plan, V1).check()).isEqualTo(CheckState.NOT_YET_CHECKED);
        assertThat(plan.snapshot().undo()).isEmpty();
        assertThat(plan.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAPublishedPlan_WhenARescheduledOrdersStopIsRemoved_ThenItIsARevisionWithThatCause() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);

        plan.removeStopOf(O2, FIRST, RemovalCause.ORDER_RESCHEDULED, LATER);

        assertThat(ordersOn(plan, V1)).containsExactly(O1);
        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.of(2)));
        assertThat(((PlanRevised) plan.pendingEvents().get(0)).cause()).isEqualTo(RevisionCause.ORDER_RESCHEDULED);
        assertThat(((PlanRevised) plan.pendingEvents().get(0)).changedRoutes()).containsExactly(v1);
    }

    @Test
    void GivenAPublishedPlan_WhenADroppedOrderIsRemoved_ThenItIsARevisionThatChangesNoRoute() {
        Plan plan = aPlan().published();

        plan.removeStopOf(O4, FIRST, RemovalCause.CORRECTED_AFTER_PUT_BACK, LATER);

        assertThat(plan.snapshot().dropped()).extracting(DroppedOrder::order).containsExactly(O5);
        PlanRevised revised = (PlanRevised) plan.pendingEvents().get(0);
        assertThat(revised.cause()).isEqualTo(RevisionCause.CORRECTED_AFTER_PUT_BACK);
        assertThat(revised.changedRoutes()).isEmpty();
    }

    // ---- an order put back onto another date ----------------------------------------------------

    @Test
    void GivenADroppedOrderPutBackOntoAnotherDate_WhenTheReadyPlanTakesItOff_ThenItsDropGoesAndThePlanPublishes() {
        Plan ready = aPlan().ready();
        ready.lockRoute(routeId(ready, V1), RouteLock.WHOLE_ROUTE, TODAY);
        Plan plan = reload(ready);

        plan.removePutBackOrder(O4, FIRST);

        assertThat(plan.snapshot().dropped()).extracting(DroppedOrder::order).containsExactly(O5);
        assertThat(plan.snapshot().undo()).isEmpty();
        assertThat(plan.pendingEvents()).isEmpty();
        assertThat(plan.pendingRevision()).isEmpty();
        Plan reloaded = reload(plan);
        reloaded.publish(PlanningFixtures.checksFor(reloaded), DISPATCHER, TODAY, LATER);
        assertThat(reloaded.status()).isEqualTo(new Plan.Status.Published(RevisionNo.FIRST));
    }

    @Test
    void GivenAReadyPlanHoldingTheAttemptAsAStop_WhenItIsPutBackOntoAnotherDate_ThenTheStopStays() {
        Plan plan = aPlan().ready();
        PlanSnapshot before = plan.snapshot();

        plan.removePutBackOrder(O1, FIRST);

        assertUnchanged(plan, before);
    }

    @Test
    void GivenAPublishedPlanWhoseStopFailed_WhenItsOrderIsPutBackOntoAnotherDate_ThenTheFailedStopStays() {
        Plan plan = aPlan().published();
        PlanSnapshot before = plan.snapshot();

        plan.removePutBackOrder(O1, FIRST);

        assertUnchanged(plan, before);
        assertThat(ordersOn(plan, V1)).containsExactly(O1, O2);
    }

    @Test
    void GivenAPublishedPlan_WhenAnOrderItDroppedIsPutBackOntoAnotherDate_ThenTheDropStaysAsTheDaysHistory() {
        Plan plan = aPlan().published();
        PlanSnapshot before = plan.snapshot();

        plan.removePutBackOrder(O4, FIRST);

        assertUnchanged(plan, before);
    }

    @Test
    void GivenAReadyPlan_WhenAnOrderItDoesNotHoldIsPutBack_ThenNothingChanges() {
        Plan plan = aPlan().ready();
        PlanSnapshot before = plan.snapshot();

        plan.removePutBackOrder(O6, FIRST);

        assertUnchanged(plan, before);
    }

    @Test
    void GivenAnOrderThePlanDoesNotHold_WhenItsStopIsRemoved_ThenNothingChanges() {
        Plan plan = aPlan().published();
        PlanSnapshot before = plan.snapshot();

        plan.removeStopOf(O6, FIRST, RemovalCause.ORDER_CANCELLED, LATER);

        assertUnchanged(plan, before);
    }

    @Test
    void GivenACompletedPlan_WhenAStopIsRemoved_ThenNothingChanges() {
        Plan plan = aPlan().completed();
        PlanSnapshot before = plan.snapshot();

        plan.removeStopOf(O1, FIRST, RemovalCause.ORDER_CANCELLED, LATER);

        assertUnchanged(plan, before);
    }

    // ---- reconcileLateOutcome -------------------------------------------------------------------

    @Test
    void GivenAFactForAStopStillOnItsRoute_WhenReconciled_ThenNothingChanges() {
        Plan plan = aPlan().published();
        PlanSnapshot before = plan.snapshot();

        Reconciliation result = plan.reconcileLateOutcome(fact(plan, O1, V1), LATER);

        assertThat(result).isEqualTo(Reconciliation.of(ReconciliationKind.UNCHANGED));
        assertUnchanged(plan, before);
    }

    @Test
    void GivenAStopNowOnAnotherRoute_WhenItsFactIsReconciled_ThenItMovesToTheServingRouteAsServedLate() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);
        RouteId v2 = routeId(plan, V2);
        LateFact fact = fact(plan, O1, V2);

        Reconciliation result = plan.reconcileLateOutcome(fact, LATER);

        assertThat(result).isEqualTo(Reconciliation.movedFrom(v1));
        assertThat(ordersOn(plan, V2)).containsExactly(O3, O1);
        assertThat(routeOf(plan, V2).stops().get(1).late()).contains(LateOutcome.SERVED_LATE);
        PlanRevised revised = (PlanRevised) plan.pendingEvents().get(0);
        assertThat(revised.cause()).isEqualTo(RevisionCause.LATE_OUTCOME);
        assertThat(revised.changedRoutes()).containsExactly(v1, v2);
    }

    @Test
    void GivenADroppedStop_WhenItsFactIsReconciled_ThenItLeavesTheDrops() {
        Plan plan = aPlan().published();
        LateFact fact = new LateFact(StopId.of(uuid(888)), O4, FIRST, routeId(plan, V2));

        Reconciliation result = plan.reconcileLateOutcome(fact, LATER);

        assertThat(result).isEqualTo(Reconciliation.of(ReconciliationKind.FROM_DROPPED));
        assertThat(ordersOn(plan, V2)).containsExactly(O3, O4);
        assertThat(plan.snapshot().dropped()).extracting(DroppedOrder::order).containsExactly(O5);
    }

    @Test
    void GivenAStopWhoseOrderWasCancelled_WhenItsFactIsReconciled_ThenItIsServedAfterCancellation() {
        Plan plan = aPlan().published();
        LateFact fact = new LateFact(StopId.of(uuid(888)), O6, FIRST, routeId(plan, V2));

        Reconciliation result = plan.reconcileLateOutcome(fact, LATER);

        assertThat(result).isEqualTo(Reconciliation.of(ReconciliationKind.AFTER_CANCELLATION));
        assertThat(routeOf(plan, V2).stops().get(1).late()).contains(LateOutcome.SERVED_AFTER_CANCELLATION);
    }

    @Test
    void GivenACompletedPlan_WhenALateFactIsReconciled_ThenItIsANewRevisionAndStaysCompleted() {
        Plan plan = aPlan().completed();

        plan.reconcileLateOutcome(fact(plan, O1, V2), LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Completed(RevisionNo.of(2)));
        assertThat(plan.pendingRevision()).isPresent();
    }

    @Test
    void GivenACancelledPlan_WhenALateFactIsReconciled_ThenItStaysCancelledWithItsRoutesUnchanged() {
        Plan plan = aPlan().cancelled();
        PlanSnapshot before = plan.snapshot();

        Reconciliation result = plan.reconcileLateOutcome(fact(plan, O1, V2), LATER);

        assertThat(result).isEqualTo(Reconciliation.of(ReconciliationKind.PLAN_CANCELLED));
        assertUnchanged(plan, before);
    }

    @Test
    void GivenAnUnknownServingRoute_WhenAFactIsReconciled_ThenRouteNotInPlan() {
        Plan plan = aPlan().published();
        RouteId unknown = RouteId.of(uuid(999));
        LateFact fact = new LateFact(stopId(plan, O1), O1, FIRST, unknown);

        assertPlanRefused(plan, () -> plan.reconcileLateOutcome(fact, LATER), new RouteNotInPlan(unknown));
    }

    // ---- recordRouteCheck -----------------------------------------------------------------------

    @Test
    void GivenAnUncheckedRoute_WhenTheRecheckJobRecordsItsCheck_ThenItIsCheckedWithNoRevision() {
        Plan handed = aPlan().published();
        handed.handOver(routeId(handed, V1), D3_STANDS, Optional.empty(), false, DISPATCHER, TODAY, LATER);
        Plan plan = reload(handed);
        RouteId v1 = routeId(plan, V1);
        Violation shift = violation("BR-06", v1);

        plan.recordRouteCheck(evaluationOf(plan).withViolation(shift).build());

        assertThat(routeOf(plan, V1).check()).isEqualTo(CheckState.CHECKED);
        assertThat(plan.snapshot().violations()).containsExactly(shift);
        assertThat(plan.pendingEvents()).isEmpty();
        assertThat(plan.pendingRevision()).isEmpty();
    }

    @Test
    void GivenTheOptimizerStillDown_WhenTheRecheckJobRecordsItsCheck_ThenNothingChanges() {
        Plan plan = aPlan().published();
        PlanSnapshot before = plan.snapshot();

        plan.recordRouteCheck(evaluationOf(plan).unavailable());

        assertUnchanged(plan, before);
    }

    // ---- cancel, complete, abandon --------------------------------------------------------------

    @Test
    void GivenAPublishedPlanNothingHappenedOn_WhenCancelled_ThenItIsCancelledWithItsRoutesAndRaisesPlanCancelled() {
        Plan plan = aPlan().published();
        List<PlanSnapshot.RouteSnapshot> routes = plan.snapshot().routes();

        plan.cancel(StartedRoutes.none(), TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Cancelled(RevisionNo.FIRST, LATER));
        assertThat(plan.snapshot().routes()).isEqualTo(routes);
        assertThat(plan.pendingEvents())
                .containsExactly(new PlanCancelled(
                        new EventId(
                                "PlanCancelled", PLAN.value(), plan.version().next(), 0),
                        LATER,
                        PLAN,
                        DATE,
                        List.of(routeId(plan, V1), routeId(plan, V2))));
        assertThat(plan.pendingRevision()).isEmpty();
    }

    @Test
    void GivenAStartedRoute_WhenThePlanIsCancelled_ThenRouteAlreadyStarted() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);

        assertPlanRefused(
                plan,
                () -> plan.cancel(new StartedRoutes(List.of(v1), List.of()), TODAY, LATER),
                new RouteAlreadyStarted(List.of(v1)));
    }

    @Test
    void GivenAStopRecordedOnADriversBehalf_WhenThePlanIsCancelled_ThenStopAlreadyReported() {
        Plan plan = aPlan().published();
        StopId o1 = stopId(plan, O1);

        assertPlanRefused(
                plan,
                () -> plan.cancel(new StartedRoutes(List.of(), List.of(o1)), TODAY, LATER),
                new StopAlreadyReported(List.of(o1)));
    }

    @Test
    void GivenAPlanWhoseDateHasPassed_WhenCancelled_ThenDateInPast() {
        Plan plan = aPlan().published();

        assertPlanRefused(plan, () -> plan.cancel(StartedRoutes.none(), TOMORROW, LATER), new DateInPast(DATE));
    }

    @Test
    void GivenAPublishedPlan_WhenItsDateIsOver_ThenItCompletes() {
        Plan plan = aPlan().published();

        plan.complete(TOMORROW);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Completed(RevisionNo.FIRST));
    }

    @Test
    void GivenAPublishedPlanOnItsDate_WhenCompleted_ThenDateNotOver() {
        Plan plan = aPlan().published();

        assertPlanRefused(plan, () -> plan.complete(TODAY), new DateNotOver(DATE));
    }

    @Test
    void GivenAReadyPlanOnItsDate_WhenAbandoned_ThenDateNotOver() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, () -> plan.abandon(TODAY), new DateNotOver(DATE));
    }

    @Test
    void GivenAReadyPlanWithEdits_WhenItsDateIsOver_ThenItIsAbandonedWithoutItsJournal() {
        Plan plan = aPlan().ready();
        plan.lockRoute(routeId(plan, V1), RouteLock.WHOLE_ROUTE, TODAY);

        plan.abandon(TOMORROW);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Abandoned());
        assertThat(plan.snapshot().undo()).isEmpty();
    }

    @Test
    void GivenAPublishedPlan_WhenAbandoned_ThenPlanNotDraftSolvingOrReady() {
        Plan plan = aPlan().published();

        assertPlanRefused(plan, () -> plan.abandon(TOMORROW), new PlanNotDraftSolvingOrReady(PLAN));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static ReoptimizationProposal standardProposal() {
        return proposal(
                List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())));
    }

    private static ReoptimizationProposal proposal(
            List<SolvedRoute> routes, List<DroppedOrder> drops, VehicleWithdrawal... withdrawn) {
        SolveJob job = aJob().reoptimizing().running();
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, List.of()));
        return job.succeed(PROPOSAL, reoptimizationResult(solution, withdrawn), LATER);
    }

    private static LateFact fact(Plan plan, OrderId order, VehicleId servedBy) {
        return new LateFact(stopId(plan, order), order, FIRST, routeId(plan, servedBy));
    }

    private static VehicleId vehicle(int n) {
        return VehicleId.of(uuid(5_000 + n));
    }

    private static AccountId driver(int n) {
        return AccountId.of(uuid(6_000 + n));
    }

    /** A published plan with 15 active and 15 withdrawn routes, so every index is taken. */
    private static PlanSnapshot withThirtyRoutes(PlanSnapshot published) {
        List<PlanSnapshot.RouteSnapshot> routes = new ArrayList<>();
        for (int n = 1; n <= 30; n++) {
            int owner = n <= 15 ? n : n - 15;
            routes.add(new PlanSnapshot.RouteSnapshot(
                    RouteId.of(uuid(7_000 + n)),
                    RouteIndex.of(n),
                    vehicle(owner),
                    driver(owner),
                    Optional.empty(),
                    CheckState.CHECKED,
                    n <= 15 ? Optional.of(new Withdrawal(WithdrawalCause.SHIFT_RAN_OUT)) : Optional.empty(),
                    Optional.empty(),
                    PlanningFixtures.routePlan(50_000),
                    List.of()));
        }
        return new PlanSnapshot(
                published.id(),
                published.version(),
                published.date(),
                published.openedAt(),
                published.status(),
                published.applied(),
                routes,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
