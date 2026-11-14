package com.dispatchflow.planning.domain;

import static com.dispatchflow.planning.domain.PlanAssertions.assertPlanAllRefused;
import static com.dispatchflow.planning.domain.PlanAssertions.assertPlanRefused;
import static com.dispatchflow.planning.domain.PlanningFixtures.DATE;
import static com.dispatchflow.planning.domain.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.PlanningFixtures.JOB;
import static com.dispatchflow.planning.domain.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.PlanningFixtures.O6;
import static com.dispatchflow.planning.domain.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.PlanningFixtures.TOMORROW;
import static com.dispatchflow.planning.domain.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.PlanningFixtures.checksFor;
import static com.dispatchflow.planning.domain.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.PlanningFixtures.routeOf;
import static com.dispatchflow.planning.domain.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.PlanningFixtures.stopPlan;
import static com.dispatchflow.planning.domain.PlanningFixtures.succeededJob;
import static com.dispatchflow.planning.domain.PlanningFixtures.violation;
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
                        List.of(), List.of(), List.of(dropped(O4, new DropReason.NoVehicleAvailable())))))
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

    private static Plan readyAppliedAt(Instant appliedAt) {
        Plan plan = aPlan().solving();
        plan.applySolveResult(succeededJob(JOB, PlanningFixtures.standardSolution()), freshIds(0), appliedAt);
        return reload(plan);
    }
}
