package com.dispatchflow.planning.domain;

import static com.dispatchflow.planning.domain.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.PlanningFixtures.DATE;
import static com.dispatchflow.planning.domain.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.PlanningFixtures.JOB;
import static com.dispatchflow.planning.domain.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.PlanningFixtures.SECOND_JOB;
import static com.dispatchflow.planning.domain.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.PlanningFixtures.TOMORROW;
import static com.dispatchflow.planning.domain.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.PlanningFixtures.checksFor;
import static com.dispatchflow.planning.domain.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.PlanningFixtures.noChanges;
import static com.dispatchflow.planning.domain.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.PlanningFixtures.roomIn;
import static com.dispatchflow.planning.domain.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.PlanningFixtures.standardSolution;
import static com.dispatchflow.planning.domain.PlanningFixtures.stopId;
import static com.dispatchflow.planning.domain.PlanningFixtures.succeededJob;
import static com.dispatchflow.planning.domain.PlanningFixtures.uuid;
import static com.dispatchflow.planning.domain.PlanningFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link Plan#restore} rebuilds every state it saved, and refuses a snapshot that breaks an at-rest invariant. */
class PlanRestoreTest {

    /**
     * Each state as its last command left it, before any reload, so the round trip is checked against
     * what the commands built rather than against what {@code restore} built before.
     */
    static Stream<Arguments> builderStates() {
        return Stream.of(
                state("draft", () -> Plan.open(PLAN, DATE, PlansOnDate.none(), TODAY, NOW)),
                state("solving", () -> {
                    Plan plan = aPlan().draft();
                    plan.requestSolve(
                            JOB,
                            SolverName.OR_TOOLS,
                            SolveLimit.DEFAULT_DAILY,
                            Seed.of(7),
                            roomIn(Lane.DAILY),
                            TODAY,
                            NOW);
                    return plan;
                }),
                state("ready", () -> {
                    Plan plan = aPlan().solving();
                    plan.applySolveResult(succeededJob(JOB, standardSolution()), freshIds(0), NOW);
                    return plan;
                }),
                state("ready with pins, a lock, a violation and both journals", PlanRestoreTest::readyEdited),
                state("solving again", () -> {
                    Plan plan = aPlan().ready();
                    plan.requestSolve(
                            SECOND_JOB,
                            SolverName.OR_TOOLS,
                            SolveLimit.DEFAULT_DAILY,
                            Seed.of(8),
                            roomIn(Lane.DAILY),
                            TODAY,
                            LATER);
                    return plan;
                }),
                state("published", () -> {
                    Plan plan = aPlan().ready();
                    plan.publish(checksFor(plan), DISPATCHER, TODAY, LATER);
                    return plan;
                }),
                state(
                        "published at revision 4 with a withdrawn route, a late outcome and a confirmed return",
                        PlanRestoreTest::publishedWithFacts),
                state("completed", () -> {
                    Plan plan = aPlan().published();
                    plan.complete(TOMORROW);
                    return plan;
                }),
                state("cancelled", () -> {
                    Plan plan = aPlan().published();
                    plan.cancel(StartedRoutes.none(), TODAY, LATER);
                    return plan;
                }),
                state("abandoned", () -> {
                    Plan plan = aPlan().ready();
                    plan.abandon(TOMORROW);
                    return plan;
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builderStates")
    void GivenAPlanInEachBuilderState_WhenItsSnapshotIsRestored_ThenTheSnapshotRoundTrips(
            String state, PlanSnapshot commanded) {
        assertThat(Plan.restore(commanded).snapshot()).isEqualTo(commanded);
    }

    @Test
    void GivenAReloadedPlanWithAnUndoneEdit_WhenItIsRedone_ThenTheEditIsBack() {
        Plan plan = reload(readyEdited());

        plan.redo(
                evaluationOf(plan)
                        .withViolation(violation("BR-03", routeId(plan, V2)))
                        .build(),
                TODAY);

        assertThat(plan.snapshot().pins())
                .containsExactly(new Pin(O4, new PinTarget.ToVehicle(V2)), new Pin(O5, new PinTarget.ToVehicle(V1)));
        assertThat(plan.snapshot().redo()).isEmpty();
        assertThat(Plan.restore(plan.snapshot()).snapshot()).isEqualTo(plan.snapshot());
    }

    private static Arguments state(String name, Supplier<Plan> commands) {
        return Arguments.of(name, commands.get().snapshot());
    }

    /**
     * A ready plan with a pin, a lock, an open violation, three edits to undo and one to redo: O5 is
     * pinned to V1, O1 moved to the front of V2 with BR-03 there, V1 locked, and O4's pin undone.
     */
    private static Plan readyEdited() {
        Plan ready = aPlan().ready();
        RouteId v1 = routeId(ready, V1);
        RouteId v2 = routeId(ready, V2);
        StopId o1 = stopId(ready, O1);
        Violation late = violation("BR-03", v2);
        ready.pin(O5, new PinTarget.ToVehicle(V1), evaluationOf(ready).build(), TODAY);
        Plan pinned = reload(ready);
        pinned.moveStop(
                o1,
                v2,
                0,
                evaluationOf(pinned).moving(o1, v2, 0).withViolation(late).build(),
                TODAY);
        Plan moved = reload(pinned);
        moved.lockRoute(v1, RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = reload(moved);
        locked.pin(
                O4,
                new PinTarget.ToVehicle(V2),
                evaluationOf(locked).withViolation(late).build(),
                TODAY);
        Plan pinnedTwice = reload(locked);
        pinnedTwice.undo(evaluationOf(pinnedTwice).withViolation(late).build(), TODAY);
        return pinnedTwice;
    }

    /**
     * A published plan at revision 4: V1 withdrawn after O1, kept on its route (revision 2); O4
     * served late on V2 from the drops (revision 3); V2 handed to D3 with a confirmed broken shift
     * (revision 4).
     */
    private static Plan publishedWithFacts() {
        Plan published = aPlan().published();
        SolveJob job = published.requestReoptimization(
                JobId.of(uuid(60)), SolverName.OR_TOOLS, Seed.of(1), roomIn(Lane.REOPTIMIZATION), TODAY, LATER);
        job.claim(LATER);
        job.searchStarted(LATER);
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(
                List.of(),
                List.of(solved(V1, D1), solved(V2, D2, O3, O2)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation()))));
        ReoptimizationProposal proposal = job.succeed(
                ProposalId.of(uuid(61)),
                reoptimizationResult(solution, new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE)),
                LATER);
        published.accept(proposal, noChanges(V2), freshIds(700), DISPATCHER, TODAY, LATER);
        Plan withdrawn = reload(published);
        RouteId v2 = routeId(withdrawn, V2);
        withdrawn.reconcileLateOutcome(new LateFact(StopId.of(uuid(62)), O4, FIRST, v2), LATER);
        Plan late = reload(withdrawn);
        Evaluation breaks = evaluationOf(late)
                .handingOver(v2, D3)
                .withViolation(violation("BR-06", v2))
                .returningAt(70_000)
                .build();
        late.handOver(v2, new DriverStanding(D3, true, true), Optional.of(breaks), true, DISPATCHER, TODAY, LATER);
        return late;
    }

    static Stream<Arguments> brokenSnapshots() {
        return Stream.of(
                broken(
                        "a draft with routes",
                        "a draft plan has no routes",
                        () -> aPlan().draft(),
                        s -> s.status(new Plan.Status.Draft()).routesFrom(aPlan().ready())),
                broken(
                        "a draft with an applied solve",
                        "a draft plan has no routes",
                        () -> aPlan().ready(),
                        s -> s.status(new Plan.Status.Draft()).routes(List.of()).dropped(List.of())),
                broken(
                        "a solving plan with routes and no applied solve",
                        "routes only from an applied solve",
                        () -> aPlan().solvingAgain(),
                        s -> s.applied(Optional.empty())),
                broken(
                        "a solving plan waiting for the job it applied",
                        "waits for a job it has not applied",
                        () -> aPlan().ready(),
                        s -> s.status(new Plan.Status.Solving(new SolveBasis(JOB, NOW)))),
                broken(
                        "a ready plan with no applied solve",
                        "a ready plan has an applied solve",
                        () -> aPlan().ready(),
                        s -> s.applied(Optional.empty())),
                broken(
                        "a published plan with a journal",
                        "only a ready or solving plan keeps a journal",
                        () -> aPlan().published(),
                        s -> s.undo(List.of(new Edit.Unpinned(O1, new PinTarget.ToVehicle(V1))))),
                broken(
                        "a published plan with no applied solve",
                        "a published plan has an applied solve",
                        () -> aPlan().published(),
                        s -> s.applied(Optional.empty())),
                broken(
                        "an abandoned plan with a journal",
                        "only a ready or solving plan keeps a journal",
                        () -> aPlan().abandoned(),
                        s -> s.undo(List.of(new Edit.Unpinned(O1, new PinTarget.ToVehicle(V1))))),
                broken(
                        "a withdrawal before publishing",
                        "belong to a published plan",
                        () -> aPlan().ready(),
                        s -> s.firstRoute(route -> new PlanSnapshot.RouteSnapshot(
                                route.id(),
                                route.index(),
                                route.vehicle(),
                                route.driver(),
                                route.lock(),
                                route.check(),
                                Optional.of(new Withdrawal(WithdrawalCause.NO_DRIVER)),
                                route.confirmedReturn(),
                                route.plan(),
                                route.stops()))),
                broken(
                        "a late outcome before publishing",
                        "belong to a published plan",
                        () -> aPlan().ready(),
                        s -> s.firstRoute(route -> new PlanSnapshot.RouteSnapshot(
                                route.id(),
                                route.index(),
                                route.vehicle(),
                                route.driver(),
                                route.lock(),
                                route.check(),
                                route.withdrawal(),
                                route.confirmedReturn(),
                                route.plan(),
                                route.stops().stream()
                                        .map(stop -> new PlanSnapshot.StopSnapshot(
                                                stop.id(),
                                                stop.order(),
                                                stop.attempt(),
                                                stop.plan(),
                                                Optional.of(LateOutcome.SERVED_LATE)))
                                        .toList()))),
                broken(
                        "an order both served and dropped",
                        "served or dropped at most once",
                        () -> aPlan().ready(),
                        s -> s.addDrop(new DroppedOrder(O1, FIRST, new DropReason.Displaced()))),
                broken(
                        "a vehicle with two routes",
                        "drives at most one route",
                        () -> aPlan().ready(),
                        s -> s.secondRoute(route -> copy(route, route.id(), route.index(), V1, route.driver()))),
                broken(
                        "a driver with two routes",
                        "drives at most one route",
                        () -> aPlan().ready(),
                        s -> s.secondRoute(route -> copy(route, route.id(), route.index(), route.vehicle(), D1))),
                broken(
                        "two routes with one ID",
                        "are unique",
                        () -> aPlan().ready(),
                        s -> s.secondRoute(route ->
                                copy(route, s.firstRouteId(), route.index(), route.vehicle(), route.driver()))),
                broken(
                        "two routes with one index",
                        "are unique",
                        () -> aPlan().ready(),
                        s -> s.secondRoute(
                                route -> copy(route, route.id(), RouteIndex.of(1), route.vehicle(), route.driver()))),
                broken(
                        "two stops with one ID",
                        "are unique",
                        () -> aPlan().ready(),
                        s -> s.secondRoute(route -> new PlanSnapshot.RouteSnapshot(
                                route.id(),
                                route.index(),
                                route.vehicle(),
                                route.driver(),
                                route.lock(),
                                route.check(),
                                route.withdrawal(),
                                route.confirmedReturn(),
                                route.plan(),
                                route.stops().stream()
                                        .map(stop -> new PlanSnapshot.StopSnapshot(
                                                s.firstStopId(),
                                                stop.order(),
                                                stop.attempt(),
                                                stop.plan(),
                                                stop.late()))
                                        .toList()))),
                broken(
                        "an order with two pins",
                        "at most one pin",
                        () -> aPlan().ready(),
                        s -> s.pins(List.of(
                                new Pin(O1, new PinTarget.ToVehicle(V1)), new Pin(O1, new PinTarget.ToVehicle(V2))))),
                broken(
                        "a violation on an unknown route",
                        "name only routes of the plan",
                        () -> aPlan().ready(),
                        s -> s.violations(List.of(violation("BR-03", RouteId.of(uuid(999)))))),
                broken(
                        "an edit of an unknown route",
                        "name only routes of the plan",
                        () -> aPlan().ready(),
                        s -> s.undo(List.of(new Edit.Unlocked(RouteId.of(uuid(999)), RouteLock.WHOLE_ROUTE)))),
                broken(
                        "a solve applied before it was requested",
                        "applied no earlier than it was requested",
                        () -> aPlan().ready(),
                        s -> s.applied(Optional.of(new AppliedSolve(new SolveBasis(JOB, NOW), NOW.minusSeconds(1))))),
                broken(
                        "a solving plan with a journal and no applied solve",
                        "a journal belongs to a plan solved before",
                        () -> aPlan().solving(),
                        s -> s.undo(List.of(new Edit.Unpinned(O1, new PinTarget.ToVehicle(V1))))),
                broken(
                        "a confirmed return before publishing",
                        "belong to a published plan",
                        () -> aPlan().ready(),
                        s -> s.firstRoute(route -> new PlanSnapshot.RouteSnapshot(
                                route.id(),
                                route.index(),
                                route.vehicle(),
                                route.driver(),
                                route.lock(),
                                route.check(),
                                route.withdrawal(),
                                Optional.of(PlanningFixtures.at(60_000)),
                                route.plan(),
                                route.stops()))),
                broken(
                        "a completed plan with no applied solve",
                        "a published plan has an applied solve",
                        () -> aPlan().completed(),
                        s -> s.applied(Optional.empty())),
                broken(
                        "a cancelled plan with no applied solve",
                        "a published plan has an applied solve",
                        () -> aPlan().cancelled(),
                        s -> s.applied(Optional.empty())),
                broken(
                        "a completed plan with a journal",
                        "only a ready or solving plan keeps a journal",
                        () -> aPlan().completed(),
                        s -> s.undo(List.of(new Edit.Unpinned(O1, new PinTarget.ToVehicle(V1))))),
                broken(
                        "a cancelled plan with a journal",
                        "only a ready or solving plan keeps a journal",
                        () -> aPlan().cancelled(),
                        s -> s.undo(List.of(new Edit.Unpinned(O1, new PinTarget.ToVehicle(V1))))),
                broken(
                        "a published plan with a lock",
                        "locks belong to a plan not yet published",
                        () -> aPlan().published(),
                        s -> s.firstRoute(route -> new PlanSnapshot.RouteSnapshot(
                                route.id(),
                                route.index(),
                                route.vehicle(),
                                route.driver(),
                                Optional.of(RouteLock.WHOLE_ROUTE),
                                route.check(),
                                route.withdrawal(),
                                route.confirmedReturn(),
                                route.plan(),
                                route.stops()))),
                broken(
                        "a ready plan with 101 journal entries",
                        "a journal holds at most 100 edits",
                        () -> aPlan().ready(),
                        s -> s.undo(pinEdits(51)).redo(pinEdits(50))),
                broken(
                        "a draft with drops only",
                        "a draft plan has no routes",
                        () -> aPlan().draft(),
                        s -> s.addDrop(new DroppedOrder(O1, FIRST, new DropReason.Displaced()))),
                broken(
                        "a draft with violations only",
                        "a draft plan has no routes",
                        () -> aPlan().draft(),
                        s -> s.violations(List.of(new Violation(
                                RuleCode.of("FR-91"),
                                Optional.empty(),
                                Optional.of(O1),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())))),
                broken(
                        "a solving plan never solved with violations",
                        "a plan never solved holds no violations",
                        () -> aPlan().solving(),
                        s -> s.violations(List.of(new Violation(
                                RuleCode.of("FR-91"),
                                Optional.empty(),
                                Optional.of(O1),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())))),
                broken(
                        "a last move whose way back is past the route's end",
                        "the last journal entries name stops where they left them",
                        PlanRestoreTest::movedToV2,
                        s -> s.undo(List.of(withFromPosition((Edit.MovedStop) s.lastUndo(), 9)))),
                broken(
                        "a last move whose stop isn't where it says",
                        "the last journal entries name stops where they left them",
                        PlanRestoreTest::movedToV2,
                        s -> s.undo(List.of(withToPosition((Edit.MovedStop) s.lastUndo(), 1)))),
                broken(
                        "a last removal whose way back is past the route's end",
                        "the last journal entries name stops where they left them",
                        PlanRestoreTest::tookOffO3,
                        s -> s.undo(List.of(withPosition((Edit.TookOff) s.lastUndo(), 9)))),
                broken(
                        "a last taken-off stop that is still on a route",
                        "the last journal entries name stops where they left them",
                        () -> aPlan().ready(),
                        s -> s.undo(List.of(
                                new Edit.TookOff(s.firstStopId(), O1, FIRST, s.firstRouteId(), 0, Optional.empty())))),
                broken(
                        "a last move of a stop the plan doesn't hold",
                        "the last journal entries name stops where they left them",
                        () -> aPlan().ready(),
                        s -> s.undo(List.of(
                                new Edit.MovedStop(StopId.of(uuid(999)), s.firstRouteId(), 0, s.firstRouteId(), 0)))),
                broken(
                        "a last undone placement whose stop is still on a route",
                        "the last journal entries name stops where they left them",
                        () -> aPlan().ready(),
                        s -> s.redo(List.of(new Edit.Placed(
                                O1, FIRST, s.firstStopId(), s.firstRouteId(), 0, new DropReason.Displaced())))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenSnapshots")
    void GivenASnapshotThatBreaksAnAtRestInvariant_WhenRestored_ThenStoredStateInvalid(
            String invariant, String detail, PlanSnapshot snapshot) {
        assertThatThrownBy(() -> Plan.restore(snapshot))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains(detail));
    }

    private static Arguments broken(
            String invariant, String detail, Supplier<Plan> from, UnaryOperator<Editor> change) {
        return Arguments.of(
                invariant,
                detail,
                change.apply(new Editor(from.get().snapshot())).build());
    }

    /** Journal entries that name no route or stop, so only their number matters. */
    private static List<Edit> pinEdits(int count) {
        return IntStream.range(0, count)
                .<Edit>mapToObj(n -> new Edit.Unpinned(O1, new PinTarget.ToVehicle(V1)))
                .toList();
    }

    /** A ready plan whose last edit moved O1 to the front of V2's route. */
    private static Plan movedToV2() {
        Plan plan = aPlan().ready();
        StopId o1 = PlanningFixtures.stopId(plan, O1);
        RouteId v2 = PlanningFixtures.routeId(plan, V2);
        plan.moveStop(
                o1, v2, 0, PlanningFixtures.evaluationOf(plan).moving(o1, v2, 0).build(), PlanningFixtures.TODAY);
        return PlanningFixtures.reload(plan);
    }

    /** A ready plan whose last edit took O3, V2's only stop, off its route. */
    private static Plan tookOffO3() {
        Plan plan = aPlan().ready();
        StopId o3 = PlanningFixtures.stopId(plan, PlanningFixtures.O3);
        plan.takeOffRoute(o3, PlanningFixtures.evaluationOf(plan).removing(o3).build(), PlanningFixtures.TODAY);
        return PlanningFixtures.reload(plan);
    }

    private static Edit.MovedStop withFromPosition(Edit.MovedStop moved, int position) {
        return new Edit.MovedStop(moved.stop(), moved.fromRoute(), position, moved.toRoute(), moved.toPosition());
    }

    private static Edit.MovedStop withToPosition(Edit.MovedStop moved, int position) {
        return new Edit.MovedStop(moved.stop(), moved.fromRoute(), moved.fromPosition(), moved.toRoute(), position);
    }

    private static Edit.TookOff withPosition(Edit.TookOff took, int position) {
        return new Edit.TookOff(took.stop(), took.order(), took.attempt(), took.route(), position, took.plan());
    }

    private static PlanSnapshot.RouteSnapshot copy(
            PlanSnapshot.RouteSnapshot route, RouteId id, RouteIndex index, VehicleId vehicle, AccountId driver) {
        return new PlanSnapshot.RouteSnapshot(
                id,
                index,
                vehicle,
                driver,
                route.lock(),
                route.check(),
                route.withdrawal(),
                route.confirmedReturn(),
                route.plan(),
                route.stops());
    }

    /** Changes one part of a snapshot at a time. */
    private static final class Editor {

        private PlanSnapshot s;

        Editor(PlanSnapshot snapshot) {
            this.s = snapshot;
        }

        Editor status(Plan.Status status) {
            s = new PlanSnapshot(
                    s.id(),
                    s.version(),
                    s.date(),
                    s.openedAt(),
                    status,
                    s.applied(),
                    s.routes(),
                    s.dropped(),
                    s.pins(),
                    s.violations(),
                    s.undo(),
                    s.redo());
            return this;
        }

        Editor applied(Optional<AppliedSolve> applied) {
            s = new PlanSnapshot(
                    s.id(),
                    s.version(),
                    s.date(),
                    s.openedAt(),
                    s.status(),
                    applied,
                    s.routes(),
                    s.dropped(),
                    s.pins(),
                    s.violations(),
                    s.undo(),
                    s.redo());
            return this;
        }

        Editor routes(List<PlanSnapshot.RouteSnapshot> routes) {
            s = new PlanSnapshot(
                    s.id(),
                    s.version(),
                    s.date(),
                    s.openedAt(),
                    s.status(),
                    s.applied(),
                    routes,
                    s.dropped(),
                    s.pins(),
                    s.violations(),
                    s.undo(),
                    s.redo());
            return this;
        }

        Editor routesFrom(Plan other) {
            return routes(other.snapshot().routes());
        }

        Editor dropped(List<DroppedOrder> dropped) {
            s = new PlanSnapshot(
                    s.id(),
                    s.version(),
                    s.date(),
                    s.openedAt(),
                    s.status(),
                    s.applied(),
                    s.routes(),
                    dropped,
                    s.pins(),
                    s.violations(),
                    s.undo(),
                    s.redo());
            return this;
        }

        Editor addDrop(DroppedOrder drop) {
            List<DroppedOrder> next = new ArrayList<>(s.dropped());
            next.add(drop);
            return dropped(next);
        }

        Editor pins(List<Pin> pins) {
            s = new PlanSnapshot(
                    s.id(),
                    s.version(),
                    s.date(),
                    s.openedAt(),
                    s.status(),
                    s.applied(),
                    s.routes(),
                    s.dropped(),
                    pins,
                    s.violations(),
                    s.undo(),
                    s.redo());
            return this;
        }

        Editor violations(List<Violation> violations) {
            s = new PlanSnapshot(
                    s.id(),
                    s.version(),
                    s.date(),
                    s.openedAt(),
                    s.status(),
                    s.applied(),
                    s.routes(),
                    s.dropped(),
                    s.pins(),
                    violations,
                    s.undo(),
                    s.redo());
            return this;
        }

        Editor undo(List<Edit> undo) {
            s = new PlanSnapshot(
                    s.id(),
                    s.version(),
                    s.date(),
                    s.openedAt(),
                    s.status(),
                    s.applied(),
                    s.routes(),
                    s.dropped(),
                    s.pins(),
                    s.violations(),
                    undo,
                    s.redo());
            return this;
        }

        Editor redo(List<Edit> redo) {
            s = new PlanSnapshot(
                    s.id(),
                    s.version(),
                    s.date(),
                    s.openedAt(),
                    s.status(),
                    s.applied(),
                    s.routes(),
                    s.dropped(),
                    s.pins(),
                    s.violations(),
                    s.undo(),
                    redo);
            return this;
        }

        Editor firstRoute(UnaryOperator<PlanSnapshot.RouteSnapshot> change) {
            return route(0, change);
        }

        Editor secondRoute(UnaryOperator<PlanSnapshot.RouteSnapshot> change) {
            return route(1, change);
        }

        private Editor route(int position, UnaryOperator<PlanSnapshot.RouteSnapshot> change) {
            List<PlanSnapshot.RouteSnapshot> next = new ArrayList<>(s.routes());
            next.set(position, change.apply(next.get(position)));
            return routes(next);
        }

        Edit lastUndo() {
            return s.undo().get(s.undo().size() - 1);
        }

        RouteId firstRouteId() {
            return s.routes().get(0).id();
        }

        StopId firstStopId() {
            return s.routes().get(0).stops().get(0).id();
        }

        PlanSnapshot build() {
            return s;
        }
    }
}
