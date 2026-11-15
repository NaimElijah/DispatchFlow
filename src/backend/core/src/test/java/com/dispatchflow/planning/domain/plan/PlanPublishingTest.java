package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertPlanAllRefused;
import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertPlanRefused;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DATE;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.JOB;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O6;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TOMORROW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.checksFor;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.stopPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.succeededJob;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.PlanningError.DateInPast;
import com.dispatchflow.planning.domain.PlanningError.OpenViolation;
import com.dispatchflow.planning.domain.PlanningError.OrderChangedSinceSolve;
import com.dispatchflow.planning.domain.PlanningError.OrderNotAccounted;
import com.dispatchflow.planning.domain.PlanningError.PlanNotReady;
import com.dispatchflow.planning.domain.PlanningError.PublishingRefused;
import com.dispatchflow.planning.domain.PlanningError.RecheckFailed;
import com.dispatchflow.planning.domain.PlanningError.RecheckUnavailable;
import com.dispatchflow.planning.domain.PlanningError.RouteNotStaffed;
import com.dispatchflow.planning.domain.PlanningError.StaleEvaluation;
import com.dispatchflow.planning.domain.PublishingStep;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.revision.PlanRevision;
import com.dispatchflow.planning.domain.revision.RevisionRoute;
import com.dispatchflow.planning.domain.solution.DropReason;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.events.PlanPublished;
import com.dispatchflow.planning.events.RevisionCause;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanPublishingTest {

    @Test
    void GivenAReadyPlanThatPassesEveryCheck_WhenPublished_ThenItIsRevisionOneWithTheRecheckedTimes() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);
        RouteId v1 = routeId(plan, V1);
        RouteId v2 = routeId(plan, V2);

        plan.publish(checks, DISPATCHER, TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.FIRST));
        assertThat(routeOf(plan, V1).stops().get(0).plan()).contains(stopPlan(30_000));
        assertThat(plan.pendingEvents())
                .containsExactly(new PlanPublished(
                        new EventId(
                                "PlanPublished", PLAN.value(), plan.version().next(), 0),
                        LATER,
                        PLAN,
                        DATE,
                        1,
                        List.of(v1, v2)));
        PlanRevision revision = plan.pendingRevision().orElseThrow();
        assertThat(revision.number()).isEqualTo(RevisionNo.FIRST);
        assertThat(revision.cause()).isEqualTo(RevisionCause.PUBLISHED);
        assertThat(revision.by()).contains(DISPATCHER);
        assertThat(revision.routes()).extracting(RevisionRoute::route).containsExactly(v1, v2);
    }

    @Test
    void GivenADraft_WhenPublished_ThenPlanNotReady() {
        Plan plan = aPlan().draft();

        assertPlanRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(
                                List.of(), List.of(), evaluationOf(plan).build()),
                        DISPATCHER,
                        TODAY,
                        LATER),
                new PlanNotReady(PLAN));
    }

    @Test
    void GivenAPlanWhoseDateHasPassed_WhenPublished_ThenDateInPast() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, () -> plan.publish(checksFor(plan), DISPATCHER, TOMORROW, LATER), new DateInPast(DATE));
    }

    @Test
    void GivenARecheckOfAnotherVersion_WhenPublished_ThenStaleEvaluation() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);
        PublishingChecks stale = new PublishingChecks(
                checks.staffing(), checks.orders(), Evaluation.checked(Version.NEW, List.of(), List.of()));

        assertPlanRefused(
                plan,
                () -> plan.publish(stale, DISPATCHER, TODAY, LATER),
                new StaleEvaluation(Version.NEW, plan.version()));
    }

    @Test
    void GivenARecheckOfThisVersionDescribingAnotherLayoutWithAViolation_WhenPublished_ThenStaleEvaluation() {
        Plan plan = aPlan().ready();
        RouteId v1 = routeId(plan, V1);
        Evaluation otherLayout = evaluationOf(plan)
                .moving(stopId(plan, O2), v1, 0)
                .withViolation(violation("BR-03", v1))
                .build();
        PublishingChecks checks = checksFor(plan);

        assertPlanRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(checks.staffing(), checks.orders(), otherLayout),
                        DISPATCHER,
                        TODAY,
                        LATER),
                new StaleEvaluation(plan.version(), plan.version()));
    }

    @Test
    void GivenALockedRoute_WhenPublished_ThenTheLockIsClearedBecauseLocksBindDailySolvesOnly() {
        Plan ready = aPlan().ready();
        ready.lockRoute(routeId(ready, V1), RouteLock.WHOLE_ROUTE, TODAY);
        Plan plan = reload(ready);

        plan.publish(checksFor(plan), DISPATCHER, TODAY, LATER);

        assertThat(plan.snapshot().routes())
                .allSatisfy(route -> assertThat(route.lock()).isEmpty());
    }

    @Test
    void GivenAnUnaccountedOrderAndAnOpenViolation_WhenPublished_ThenOnlyTheOrdersStepIsListed() {
        Plan plan = withOpenViolation();
        PublishingChecks checks = checksFor(plan);
        List<DateOrder> withAnUnsolvedOrder = new ArrayList<>(checks.orders());
        withAnUnsolvedOrder.add(new DateOrder(O6, FIRST, NOW.minusSeconds(3_600), NOW.minusSeconds(3_600)));

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(checks.staffing(), withAnUnsolvedOrder, checks.recheck()),
                        DISPATCHER,
                        TODAY,
                        LATER),
                new PublishingRefused(PublishingStep.ORDERS),
                List.of(new OrderNotAccounted(O6, FIRST)));
    }

    @Test
    void GivenAnOpenViolationAndAFailingRecheck_WhenPublished_ThenOnlyTheViolationsStepIsListed() {
        Plan plan = withOpenViolation();
        PublishingChecks checks = checksFor(plan);
        Evaluation failing = evaluationOf(plan)
                .withViolation(violation("BR-05", routeId(plan, V1)))
                .build();

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(checks.staffing(), checks.orders(), failing), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.VIOLATIONS),
                List.of(new OpenViolation(RuleCode.of("BR-03"), Optional.of(routeId(plan, V2)), Optional.empty())));
    }

    @Test
    void GivenRefusalsAtTheStaffingAndOrdersSteps_WhenPublishedOnlyTheStaffingStepIsListed_ThenPublishingRefused() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);
        List<RouteStaffing> staffing = new ArrayList<>(checks.staffing());
        staffing.set(1, new RouteStaffing(V2, staffing.get(1).driver(), false));
        List<DateOrder> withALateOrder = new ArrayList<>(checks.orders());
        withALateOrder.add(new DateOrder(O6, FIRST, LATER, LATER));

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(staffing, withALateOrder, checks.recheck()), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.STAFFING),
                List.of(new RouteNotStaffed(routeId(plan, V2), V2)));
    }

    @Test
    void GivenADriverWhoIsNoLongerTheVehiclesDriver_WhenPublished_ThenRouteNotStaffed() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);
        List<RouteStaffing> staffing = List.of(checks.staffing().get(1));

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(staffing, checks.orders(), checks.recheck()), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.STAFFING),
                List.of(new RouteNotStaffed(routeId(plan, V1), V1)));
    }

    @Test
    void GivenAnOrderTheSolveNeverSaw_WhenPublished_ThenOrderNotAccounted() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);
        List<DateOrder> orders = new ArrayList<>(checks.orders());
        Instant beforeTheSolve = NOW.minusSeconds(60);
        orders.add(new DateOrder(O6, FIRST, beforeTheSolve, beforeTheSolve));

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(checks.staffing(), orders, checks.recheck()), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.ORDERS),
                List.of(new OrderNotAccounted(O6, FIRST)));
    }

    @Test
    void GivenAnOrderPlacedAfterTheSolveWasRequested_WhenPublished_ThenOrderChangedSinceSolve() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);
        List<DateOrder> orders = new ArrayList<>(checks.orders());
        orders.add(new DateOrder(O6, FIRST, LATER, LATER));

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(checks.staffing(), orders, checks.recheck()), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.ORDERS),
                List.of(new OrderNotAccounted(O6, FIRST), new OrderChangedSinceSolve(O6)));
    }

    @Test
    void GivenAnOrderChangedWhileThePlanWasSolving_WhenPublished_ThenOrderChangedSinceSolve() {
        Plan plan = readyAppliedAt(LATER);
        PublishingChecks checks = checksFor(plan);
        Instant whileSolving = NOW.plusSeconds(30);
        List<DateOrder> orders = checks.orders().stream()
                .map(order ->
                        order.order().equals(O3) ? new DateOrder(O3, FIRST, order.placedAt(), whileSolving) : order)
                .toList();

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(checks.staffing(), orders, checks.recheck()), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.ORDERS),
                List.of(new OrderChangedSinceSolve(O3)));
    }

    @Test
    void GivenAnOrderChangedAfterTheSolveWasApplied_WhenPublished_ThenTheRecheckDecidesAndItPublishes() {
        Plan plan = readyAppliedAt(LATER);
        PublishingChecks checks = checksFor(plan);
        Instant afterTheSolve = LATER.plusSeconds(30);
        List<DateOrder> orders = checks.orders().stream()
                .map(order ->
                        order.order().equals(O3) ? new DateOrder(O3, FIRST, order.placedAt(), afterTheSolve) : order)
                .toList();

        plan.publish(
                new PublishingChecks(checks.staffing(), orders, checks.recheck()), DISPATCHER, TODAY, afterTheSolve);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.FIRST));
    }

    @Test
    void GivenAStopWhoseOrderLeftTheDate_WhenPublished_ThenOrderChangedSinceSolve() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);
        List<DateOrder> withoutO1 = checks.orders().stream()
                .filter(order -> !order.order().equals(O1))
                .toList();

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(checks.staffing(), withoutO1, checks.recheck()), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.ORDERS),
                List.of(new OrderChangedSinceSolve(O1)));
    }

    @Test
    void GivenAnOpenViolation_WhenPublished_ThenOpenViolation() {
        Plan ready = aPlan().ready();
        StopId o1 = stopId(ready, O1);
        RouteId v2 = routeId(ready, V2);
        Violation late = violation("BR-03", v2);
        ready.moveStop(
                o1,
                v2,
                0,
                evaluationOf(ready).moving(o1, v2, 0).withViolation(late).build(),
                TODAY);
        Plan plan = reload(ready);

        assertPlanAllRefused(
                plan,
                () -> plan.publish(checksFor(plan), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.VIOLATIONS),
                List.of(new OpenViolation(RuleCode.of("BR-03"), Optional.of(v2), Optional.empty())));
    }

    @Test
    void GivenTheOptimizerIsDown_WhenPublished_ThenRecheckUnavailable() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(
                                checks.staffing(),
                                checks.orders(),
                                evaluationOf(plan).unavailable()),
                        DISPATCHER,
                        TODAY,
                        LATER),
                new PublishingRefused(PublishingStep.RECHECK),
                List.of(new RecheckUnavailable()));
    }

    @Test
    void GivenARecheckThatFindsBrokenRulesEachListed_WhenPublished_ThenRecheckFailed() {
        Plan plan = aPlan().ready();
        PublishingChecks checks = checksFor(plan);
        RouteId v1 = routeId(plan, V1);
        RouteId v2 = routeId(plan, V2);
        Evaluation broken = evaluationOf(plan)
                .withViolation(violation("BR-05", v1))
                .withViolation(violation("BR-06", v2))
                .build();

        assertPlanAllRefused(
                plan,
                () -> plan.publish(
                        new PublishingChecks(checks.staffing(), checks.orders(), broken), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.RECHECK),
                List.of(
                        new RecheckFailed(RuleCode.of("BR-05"), Optional.of(v1), Optional.empty()),
                        new RecheckFailed(RuleCode.of("BR-06"), Optional.of(v2), Optional.empty())));
    }

    @Test
    void GivenAPlanWithoutRoutes_WhenPublishedWhileTheOptimizerIsDown_ThenItPublishesWithoutARecheck() {
        Plan plan = aPlan().solvedAs(ValidatedSolution.of(ValidationReport.of(
                        List.of(), List.of(), List.of(dropped(O4, new DropReason.NoVehicleAvailable())), List.of())))
                .ready();
        PublishingChecks checks = checksFor(plan);

        plan.publish(
                new PublishingChecks(
                        List.of(), checks.orders(), evaluationOf(plan).unavailable()),
                DISPATCHER,
                TODAY,
                LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.FIRST));
    }

    @Test
    void GivenAnOrderPlacedTheMomentTheSolveWasRequested_WhenPublished_ThenItIsNotChangedSinceTheSolve() {
        Plan plan = readyAppliedAt(LATER);

        plan.publish(withO3(plan, NOW, NOW), DISPATCHER, TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.FIRST));
    }

    @Test
    void GivenAnOrderLastChangedTheMomentTheSolveWasRequested_WhenPublished_ThenItIsNotChangedSinceTheSolve() {
        Plan plan = readyAppliedAt(LATER);

        plan.publish(withO3(plan, NOW.minusSeconds(60), NOW), DISPATCHER, TODAY, LATER);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Published(RevisionNo.FIRST));
    }

    @Test
    void GivenAnOrderLastChangedTheMomentTheResultWasApplied_WhenPublished_ThenOrderChangedSinceSolve() {
        Plan plan = readyAppliedAt(LATER);

        assertPlanAllRefused(
                plan,
                () -> plan.publish(withO3(plan, NOW.minusSeconds(60), LATER), DISPATCHER, TODAY, LATER),
                new PublishingRefused(PublishingStep.ORDERS),
                List.of(new OrderChangedSinceSolve(O3)));
    }

    private static PublishingChecks withO3(Plan plan, Instant placedAt, Instant lastChangedAt) {
        PublishingChecks checks = checksFor(plan);
        List<DateOrder> orders = checks.orders().stream()
                .map(order -> order.order().equals(O3) ? new DateOrder(O3, FIRST, placedAt, lastChangedAt) : order)
                .toList();
        return new PublishingChecks(checks.staffing(), orders, checks.recheck());
    }

    /** A ready plan whose O1 was moved to the front of V2, leaving BR-03 open there. */
    private static Plan withOpenViolation() {
        Plan ready = aPlan().ready();
        StopId o1 = stopId(ready, O1);
        RouteId v2 = routeId(ready, V2);
        ready.moveStop(
                o1,
                v2,
                0,
                evaluationOf(ready)
                        .moving(o1, v2, 0)
                        .withViolation(violation("BR-03", v2))
                        .build(),
                TODAY);
        return reload(ready);
    }

    private static Plan readyAppliedAt(Instant appliedAt) {
        Plan plan = aPlan().solving();
        plan.applySolveResult(succeededJob(JOB, PlanningFixtures.standardSolution()), freshIds(0), appliedAt);
        return reload(plan);
    }
}
