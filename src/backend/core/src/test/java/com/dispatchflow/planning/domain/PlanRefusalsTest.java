package com.dispatchflow.planning.domain;

import static com.dispatchflow.planning.domain.PlanAssertions.assertPlanRefused;
import static com.dispatchflow.planning.domain.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.PlanningFixtures.DATE;
import static com.dispatchflow.planning.domain.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.PlanningFixtures.SECOND_JOB;
import static com.dispatchflow.planning.domain.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.PlanningFixtures.TOMORROW;
import static com.dispatchflow.planning.domain.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.PlanningFixtures.aJob;
import static com.dispatchflow.planning.domain.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.PlanningFixtures.checksFor;
import static com.dispatchflow.planning.domain.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.PlanningFixtures.noChanges;
import static com.dispatchflow.planning.domain.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.PlanningFixtures.roomIn;
import static com.dispatchflow.planning.domain.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.PlanningFixtures.uuid;

import com.dispatchflow.planning.domain.PlanningError.DateInPast;
import com.dispatchflow.planning.domain.PlanningError.DriverAlreadyHasRoute;
import com.dispatchflow.planning.domain.PlanningError.OrderAlreadyInPlan;
import com.dispatchflow.planning.domain.PlanningError.PositionOutOfRange;
import com.dispatchflow.planning.domain.PlanningError.RouteLocked;
import com.dispatchflow.planning.domain.PlanningError.RouteNotInPlan;
import com.dispatchflow.planning.domain.PlanningError.StaleEvaluation;
import com.dispatchflow.planning.domain.PlanningError.ValidatorUnavailable;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Each refusal checked on every command that has it (DL §14), through {@link
 * PlanAssertions#assertPlanRefused}: the plan is unchanged and raised nothing.
 */
class PlanRefusalsTest {

    private static final StopId NEW_STOP = StopId.of(uuid(777));
    private static final RouteId UNKNOWN_ROUTE = RouteId.of(uuid(778));

    /** A command on a plan, given the evaluation it should be judged by and the day it runs on. */
    private interface Command {
        void run(Plan plan, Evaluation evaluation, LocalDate today);
    }

    private static Arguments command(String name, Supplier<Plan> state, Command command) {
        return Arguments.of(name, state, command);
    }

    // ---- DateInPast -----------------------------------------------------------------------------

    static Stream<Arguments> datedCommands() {
        return Stream.of(
                command(
                        "requestSolve",
                        () -> aPlan().draft(),
                        (plan, e, today) -> plan.requestSolve(
                                SECOND_JOB,
                                SolverName.OR_TOOLS,
                                SolveLimit.DEFAULT_DAILY,
                                Seed.of(1),
                                roomIn(Lane.DAILY),
                                today,
                                LATER)),
                command(
                        "requestReoptimization",
                        () -> aPlan().published(),
                        (plan, e, today) -> plan.requestReoptimization(
                                SECOND_JOB,
                                SolverName.OR_TOOLS,
                                Seed.of(1),
                                roomIn(Lane.REOPTIMIZATION),
                                today,
                                LATER)),
                command(
                        "moveStop",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.moveStop(stopId(plan, O1), routeId(plan, V2), 0, e, today)),
                command(
                        "takeOffRoute",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.takeOffRoute(stopId(plan, O1), e, today)),
                command(
                        "placeDropped",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.placeDropped(O4, FIRST, routeId(plan, V2), 0, NEW_STOP, e, today)),
                command(
                        "pin",
                        () -> aPlan().draft(),
                        (plan, e, today) -> plan.pin(O1, new PinTarget.ToVehicle(V1), e, today)),
                command("unpin", () -> aPlan().draft(), (plan, e, today) -> plan.unpin(O1, e, today)),
                command(
                        "lockRoute",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.lockRoute(routeId(plan, V1), RouteLock.WHOLE_ROUTE, today)),
                command(
                        "unlockRoute",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.unlockRoute(routeId(plan, V1), today)),
                command("undo", () -> aPlan().ready(), (plan, e, today) -> plan.undo(e, today)),
                command("redo", () -> aPlan().ready(), (plan, e, today) -> plan.redo(e, today)),
                command(
                        "publish",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.publish(checksFor(plan), DISPATCHER, today, LATER)),
                command(
                        "accept",
                        () -> aPlan().published(),
                        (plan, e, today) ->
                                plan.accept(proposal(), noChanges(V1, V2), freshIds(700), DISPATCHER, today, LATER)),
                command(
                        "handOver",
                        () -> aPlan().published(),
                        (plan, e, today) -> plan.handOver(
                                routeId(plan, V1),
                                new DriverStanding(D3, true, true),
                                Optional.empty(),
                                false,
                                DISPATCHER,
                                today,
                                LATER)),
                command(
                        "cancel",
                        () -> aPlan().published(),
                        (plan, e, today) -> plan.cancel(StartedRoutes.none(), today, LATER)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("datedCommands")
    void GivenEachDispatcherCommandOnADateThatHasPassed_WhenItRuns_ThenDateInPast(
            String name, Supplier<Plan> state, Command command) {
        Plan plan = state.get();
        Evaluation evaluation = evaluationOf(plan).build();

        assertPlanRefused(plan, () -> command.run(plan, evaluation, TOMORROW), new DateInPast(DATE));
    }

    // ---- ValidatorUnavailable -------------------------------------------------------------------

    static Stream<Arguments> editsThatNeedAnEvaluation() {
        return Stream.of(
                command(
                        "moveStop",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.moveStop(stopId(plan, O1), routeId(plan, V2), 0, e, today)),
                command(
                        "takeOffRoute",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.takeOffRoute(stopId(plan, O1), e, today)),
                command(
                        "placeDropped",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.placeDropped(O4, FIRST, routeId(plan, V2), 0, NEW_STOP, e, today)),
                command(
                        "pin in draft",
                        () -> aPlan().draft(),
                        (plan, e, today) -> plan.pin(O1, new PinTarget.ToVehicle(V1), e, today)),
                command(
                        "pin",
                        () -> aPlan().ready(),
                        (plan, e, today) -> plan.pin(O1, new PinTarget.ToVehicle(V1), e, today)),
                command("unpin", PlanRefusalsTest::pinnedReady, (plan, e, today) -> plan.unpin(O1, e, today)),
                command("undo", PlanRefusalsTest::lockedAndJournaled, (plan, e, today) -> plan.undo(e, today)),
                command("redo", PlanRefusalsTest::lockedThenUndone, (plan, e, today) -> plan.redo(e, today)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("editsThatNeedAnEvaluation")
    void GivenEachEditWhileTheOptimizerIsDown_WhenItRuns_ThenValidatorUnavailable(
            String name, Supplier<Plan> state, Command command) {
        Plan plan = state.get();
        Evaluation unavailable = evaluationOf(plan).unavailable();

        assertPlanRefused(plan, () -> command.run(plan, unavailable, TODAY), new ValidatorUnavailable());
    }

    // ---- StaleEvaluation, step 6 ----------------------------------------------------------------

    static Stream<Arguments> commandsJudgedByAnEvaluation() {
        return Stream.concat(
                editsThatNeedAnEvaluation(),
                Stream.of(
                        command("publish", () -> aPlan().ready(), (plan, e, today) -> {
                            PublishingChecks checks = checksFor(plan);
                            plan.publish(
                                    new PublishingChecks(checks.staffing(), checks.orders(), e),
                                    DISPATCHER,
                                    today,
                                    LATER);
                        }),
                        command(
                                "handOver",
                                () -> aPlan().published(),
                                (plan, e, today) -> plan.handOver(
                                        routeId(plan, V1),
                                        new DriverStanding(D3, true, true),
                                        Optional.of(e),
                                        false,
                                        DISPATCHER,
                                        today,
                                        LATER)),
                        command(
                                "recordRouteCheck",
                                () -> aPlan().published(),
                                (plan, e, today) -> plan.recordRouteCheck(e))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandsJudgedByAnEvaluation")
    void GivenAnEvaluationOfThisVersionThatDescribesAnotherPlan_WhenEachCommandRuns_ThenStaleEvaluation(
            String name, Supplier<Plan> state, Command command) {
        Plan plan = state.get();
        Evaluation another = Evaluation.checked(
                plan.version(),
                List.of(),
                List.of(new RouteTimes(UNKNOWN_ROUTE, V1, D1, PlanningFixtures.routePlan(50_000), List.of())));

        assertPlanRefused(
                plan, () -> command.run(plan, another, TODAY), new StaleEvaluation(plan.version(), plan.version()));
    }

    @Test
    void GivenAnEvaluationJudgedWithTheOldDriver_WhenARouteIsHandedOver_ThenStaleEvaluation() {
        Plan plan = aPlan().published();
        Evaluation oldDriver = evaluationOf(plan).build();

        assertPlanRefused(
                plan,
                () -> plan.handOver(
                        routeId(plan, V1),
                        new DriverStanding(D3, true, true),
                        Optional.of(oldDriver),
                        false,
                        DISPATCHER,
                        TODAY,
                        LATER),
                new StaleEvaluation(plan.version(), plan.version()));
    }

    // ---- placeDropped, unlockRoute and handOver -------------------------------------------------

    @Test
    void GivenAnUnknownRoute_WhenADroppedOrderIsPlacedOnIt_ThenRouteNotInPlan() {
        Plan plan = aPlan().ready();

        assertPlanRefused(
                plan,
                () -> plan.placeDropped(
                        O4,
                        FIRST,
                        UNKNOWN_ROUTE,
                        0,
                        NEW_STOP,
                        evaluationOf(plan).build(),
                        TODAY),
                new RouteNotInPlan(UNKNOWN_ROUTE));
    }

    @Test
    void GivenAPositionPastTheRoutesEnd_WhenADroppedOrderIsPlacedThere_ThenPositionOutOfRange() {
        Plan plan = aPlan().ready();
        RouteId v2 = routeId(plan, V2);

        assertPlanRefused(
                plan,
                () -> plan.placeDropped(
                        O4, FIRST, v2, 2, NEW_STOP, evaluationOf(plan).build(), TODAY),
                new PositionOutOfRange(v2, 2));
    }

    @Test
    void GivenALockedRoute_WhenADroppedOrderIsPlacedOnIt_ThenRouteLocked() {
        Plan ready = aPlan().ready();
        RouteId v2 = routeId(ready, V2);
        ready.lockRoute(v2, RouteLock.WHOLE_ROUTE, TODAY);
        Plan plan = reload(ready);

        assertPlanRefused(
                plan,
                () -> plan.placeDropped(
                        O4,
                        FIRST,
                        v2,
                        0,
                        NEW_STOP,
                        evaluationOf(plan).inserting(v2, 0, NEW_STOP).build(),
                        TODAY),
                new RouteLocked(v2));
    }

    @Test
    void GivenAnUnknownRoute_WhenItIsUnlocked_ThenRouteNotInPlan() {
        Plan plan = aPlan().ready();

        assertPlanRefused(plan, () -> plan.unlockRoute(UNKNOWN_ROUTE, TODAY), new RouteNotInPlan(UNKNOWN_ROUTE));
    }

    @Test
    void GivenARoutesOwnDriver_WhenTheRouteIsHandedOverToThem_ThenDriverAlreadyHasRoute() {
        Plan plan = aPlan().published();
        RouteId v1 = routeId(plan, V1);

        assertPlanRefused(
                plan,
                () -> plan.handOver(
                        v1, new DriverStanding(D1, true, true), Optional.empty(), false, DISPATCHER, TODAY, LATER),
                new DriverAlreadyHasRoute(D1, v1));
    }

    // ---- accept ---------------------------------------------------------------------------------

    @Test
    void GivenAProposalGivingADriverTwoRoutes_WhenAccepted_ThenDriverAlreadyHasRoute() {
        Plan plan = aPlan().published();

        assertPlanRefused(
                plan,
                () -> plan.accept(
                        proposalOf(List.of(solved(V1, D1, O1), solved(V2, D1, O3))),
                        noChanges(V1, V2),
                        freshIds(700),
                        DISPATCHER,
                        TODAY,
                        LATER),
                new DriverAlreadyHasRoute(D1, routeId(plan, V1)));
    }

    @Test
    void GivenAProposalServingAnOrderTwice_WhenAccepted_ThenOrderAlreadyInPlan() {
        Plan plan = aPlan().published();

        assertPlanRefused(
                plan,
                () -> plan.accept(
                        proposalOf(List.of(solved(V1, D1, O1), solved(V2, D2, O1))),
                        noChanges(V1, V2),
                        freshIds(700),
                        DISPATCHER,
                        TODAY,
                        LATER),
                new OrderAlreadyInPlan(O1, FIRST));
    }

    private static ReoptimizationProposal proposal() {
        return proposalOf(List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)));
    }

    private static ReoptimizationProposal proposalOf(List<SolvedRoute> routes) {
        SolveJob job = aJob().reoptimizing().running();
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(List.of(), routes, List.of()));
        return job.succeed(PROPOSAL, reoptimizationResult(solution), LATER);
    }

    private static Plan pinnedReady() {
        Plan plan = aPlan().ready();
        plan.pin(O1, new PinTarget.ToVehicle(V1), evaluationOf(plan).build(), TODAY);
        return reload(plan);
    }

    private static Plan lockedAndJournaled() {
        Plan plan = aPlan().ready();
        plan.lockRoute(routeId(plan, V1), RouteLock.WHOLE_ROUTE, TODAY);
        return reload(plan);
    }

    private static Plan lockedThenUndone() {
        Plan plan = lockedAndJournaled();
        plan.undo(evaluationOf(plan).build(), TODAY);
        return reload(plan);
    }
}
