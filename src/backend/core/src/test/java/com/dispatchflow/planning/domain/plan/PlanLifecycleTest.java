package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertUnchanged;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.JOB;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.SECOND_JOB;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TOMORROW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aJob;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.checksFor;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.noChanges;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.roomIn;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.standardSolution;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.succeededJob;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.PlanningError.PlanNotDraftOrReady;
import com.dispatchflow.planning.domain.PlanningError.PlanNotDraftSolvingOrReady;
import com.dispatchflow.planning.domain.PlanningError.PlanNotPublished;
import com.dispatchflow.planning.domain.PlanningError.PlanNotReady;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.basis.SolveBasis;
import com.dispatchflow.planning.domain.reconciliation.LateFact;
import com.dispatchflow.planning.domain.reconciliation.Reconciliation;
import com.dispatchflow.planning.domain.reconciliation.ReconciliationKind;
import com.dispatchflow.planning.domain.solution.DropReason;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.PinTarget;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every state of a plan × every command, against the matrix ADR-101 approved (DL §9.1, §14). A
 * refused cell runs the command alone and expects its error; an accepted cell runs the command's
 * setup, then the command, and checks the exact state it leaves (revision included) and what the
 * command returned. A refused or unchanged call leaves the snapshot and the pending events as they
 * were.
 */
class PlanLifecycleTest {

    /** What a cell expects. */
    private sealed interface Expect {}

    /** The command is accepted and leaves this exact state. */
    private record Becomes(Plan.Status status) implements Expect {}

    /** The command is accepted and changes nothing. */
    private record NoChange() implements Expect {}

    /** The command is refused with this error. */
    private record Refused(Class<? extends PlanningError> error) implements Expect {}

    /** One command: the setup an accepted cell needs, the command, and a check of what it returns. */
    private record Row(
            Consumer<Plan> setup,
            Function<Plan, Object> command,
            BiConsumer<Plan, Object> returned,
            List<Expect> cells) {}

    private static final Map<String, Supplier<Plan>> STATES = new LinkedHashMap<>();
    private static final Map<String, Row> ROWS = new LinkedHashMap<>();

    private static final Consumer<Plan> NO_SETUP = plan -> {};
    private static final BiConsumer<Plan, Object> NOTHING_RETURNED =
            (plan, result) -> assertThat(result).isNull();

    private static final Expect SAME = new NoChange();
    private static final Expect NOT_DRAFT_OR_READY = new Refused(PlanNotDraftOrReady.class);
    private static final Expect NOT_READY = new Refused(PlanNotReady.class);
    private static final Expect NOT_PUBLISHED = new Refused(PlanNotPublished.class);
    private static final Expect NOT_UNPUBLISHED = new Refused(PlanNotDraftSolvingOrReady.class);
    private static final Expect DRAFT = new Becomes(new Plan.Status.Draft());
    private static final Expect READY = new Becomes(new Plan.Status.Ready());
    private static final Expect ABANDONED = new Becomes(new Plan.Status.Abandoned());
    private static final Expect SOLVING_AGAIN = new Becomes(new Plan.Status.Solving(new SolveBasis(SECOND_JOB, LATER)));
    private static final Expect REVISION_1 = new Becomes(new Plan.Status.Published(RevisionNo.FIRST));
    private static final Expect REVISION_2 = new Becomes(new Plan.Status.Published(RevisionNo.of(2)));
    private static final Expect COMPLETED_1 = new Becomes(new Plan.Status.Completed(RevisionNo.FIRST));
    private static final Expect COMPLETED_2 = new Becomes(new Plan.Status.Completed(RevisionNo.of(2)));
    private static final Expect CANCELLED = new Becomes(new Plan.Status.Cancelled(RevisionNo.FIRST, LATER));

    static {
        STATES.put("draft", () -> aPlan().draft());
        STATES.put("solving", () -> aPlan().solving());
        STATES.put("ready", () -> aPlan().ready());
        STATES.put("published", () -> aPlan().published());
        STATES.put("completed", () -> aPlan().completed());
        STATES.put("cancelled", () -> aPlan().cancelled());
        STATES.put("abandoned", () -> aPlan().abandoned());

        // Each row's cells are in STATES' order: draft, solving, ready, published, completed, cancelled, abandoned.
        row(
                "requestSolve",
                NO_SETUP,
                plan -> plan.requestSolve(
                        SECOND_JOB,
                        SolverName.OR_TOOLS,
                        SolveLimit.DEFAULT_DAILY,
                        Seed.of(1),
                        roomIn(Lane.DAILY),
                        TODAY,
                        LATER),
                (plan, job) -> queuedIn((SolveJob) job, Lane.DAILY),
                SOLVING_AGAIN,
                NOT_DRAFT_OR_READY,
                SOLVING_AGAIN,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY);
        row(
                "requestReoptimization",
                NO_SETUP,
                plan -> plan.requestReoptimization(
                        SECOND_JOB, SolverName.OR_TOOLS, Seed.of(1), roomIn(Lane.REOPTIMIZATION), TODAY, LATER),
                (plan, job) -> {
                    queuedIn((SolveJob) job, Lane.REOPTIMIZATION);
                    assertThat(((SolveJob) job).snapshot().basisRevision()).contains(RevisionNo.FIRST);
                },
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                SAME,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED);
        row(
                "applySolveResult",
                NO_SETUP,
                plan -> run(() -> plan.applySolveResult(succeededJob(JOB, standardSolution()), freshIds(400), LATER)),
                NOTHING_RETURNED,
                SAME,
                READY,
                SAME,
                SAME,
                SAME,
                SAME,
                SAME);
        row(
                "solveFailed",
                NO_SETUP,
                plan -> run(() -> plan.solveFailed(JOB)),
                NOTHING_RETURNED,
                SAME,
                DRAFT,
                SAME,
                SAME,
                SAME,
                SAME,
                SAME);
        row(
                "moveStop",
                NO_SETUP,
                PlanLifecycleTest::moveStop,
                NOTHING_RETURNED,
                NOT_READY,
                NOT_READY,
                READY,
                NOT_READY,
                NOT_READY,
                NOT_READY,
                NOT_READY);
        row(
                "takeOffRoute",
                NO_SETUP,
                PlanLifecycleTest::takeOff,
                NOTHING_RETURNED,
                NOT_READY,
                NOT_READY,
                READY,
                NOT_READY,
                NOT_READY,
                NOT_READY,
                NOT_READY);
        row(
                "placeDropped",
                NO_SETUP,
                PlanLifecycleTest::place,
                NOTHING_RETURNED,
                NOT_READY,
                NOT_READY,
                READY,
                NOT_READY,
                NOT_READY,
                NOT_READY,
                NOT_READY);
        row(
                "pin",
                NO_SETUP,
                PlanLifecycleTest::pin,
                NOTHING_RETURNED,
                DRAFT,
                NOT_DRAFT_OR_READY,
                READY,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY);
        row(
                "unpin",
                PlanLifecycleTest::pin,
                plan -> run(() -> plan.unpin(O1, evaluationOf(plan).build(), TODAY)),
                NOTHING_RETURNED,
                DRAFT,
                NOT_DRAFT_OR_READY,
                READY,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY,
                NOT_DRAFT_OR_READY);
        row(
                "lockRoute",
                NO_SETUP,
                PlanLifecycleTest::lock,
                NOTHING_RETURNED,
                NOT_READY,
                NOT_READY,
                READY,
                NOT_READY,
                NOT_READY,
                NOT_READY,
                NOT_READY);
        row(
                "unlockRoute",
                PlanLifecycleTest::lock,
                plan -> run(() -> plan.unlockRoute(route(plan, V1), TODAY)),
                NOTHING_RETURNED,
                NOT_READY,
                NOT_READY,
                READY,
                NOT_READY,
                NOT_READY,
                NOT_READY,
                NOT_READY);
        row(
                "undo",
                PlanLifecycleTest::lock,
                plan -> run(() -> plan.undo(evaluationOf(plan).build(), TODAY)),
                NOTHING_RETURNED,
                NOT_READY,
                NOT_READY,
                READY,
                NOT_READY,
                NOT_READY,
                NOT_READY,
                NOT_READY);
        row(
                "redo",
                plan -> {
                    lock(plan);
                    plan.undo(evaluationOf(plan).build(), TODAY);
                },
                plan -> run(() -> plan.redo(evaluationOf(plan).build(), TODAY)),
                NOTHING_RETURNED,
                NOT_READY,
                NOT_READY,
                READY,
                NOT_READY,
                NOT_READY,
                NOT_READY,
                NOT_READY);
        row(
                "publish",
                NO_SETUP,
                plan -> run(() -> plan.publish(checksFor(plan), DISPATCHER, TODAY, LATER)),
                NOTHING_RETURNED,
                NOT_READY,
                NOT_READY,
                REVISION_1,
                NOT_READY,
                NOT_READY,
                NOT_READY,
                NOT_READY);
        row(
                "accept",
                NO_SETUP,
                plan -> run(() -> plan.accept(proposal(), noChanges(V1, V2), freshIds(400), DISPATCHER, TODAY, LATER)),
                NOTHING_RETURNED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                REVISION_2,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED);
        row(
                "handOver",
                NO_SETUP,
                plan -> run(() -> plan.handOver(
                        route(plan, V1),
                        new DriverStanding(D3, true, true),
                        Optional.empty(),
                        false,
                        DISPATCHER,
                        TODAY,
                        LATER)),
                NOTHING_RETURNED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                REVISION_2,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED);
        row(
                "removeStopOf",
                NO_SETUP,
                plan -> run(() -> plan.removeStopOf(O1, FIRST, RemovalCause.ORDER_CANCELLED, LATER)),
                NOTHING_RETURNED,
                SAME,
                SAME,
                READY,
                REVISION_2,
                SAME,
                SAME,
                SAME);
        row(
                "removePutBackOrder",
                NO_SETUP,
                plan -> run(() -> plan.removePutBackOrder(O4, FIRST)),
                NOTHING_RETURNED,
                SAME,
                SAME,
                READY,
                SAME,
                SAME,
                SAME,
                SAME);
        row(
                "reconcileLateOutcome",
                NO_SETUP,
                plan -> plan.reconcileLateOutcome(new LateFact(stop(plan, O1), O1, FIRST, route(plan, V2)), LATER),
                PlanLifecycleTest::reconciled,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                REVISION_2,
                COMPLETED_2,
                SAME,
                NOT_PUBLISHED);
        row(
                "recordRouteCheck",
                NO_SETUP,
                plan -> run(() -> plan.recordRouteCheck(evaluationOf(plan).build())),
                NOTHING_RETURNED,
                SAME,
                SAME,
                READY,
                REVISION_1,
                SAME,
                SAME,
                SAME);
        row(
                "cancel",
                NO_SETUP,
                plan -> run(() -> plan.cancel(StartedRoutes.none(), TODAY, LATER)),
                NOTHING_RETURNED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                CANCELLED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED);
        row(
                "complete",
                NO_SETUP,
                plan -> run(() -> plan.complete(TOMORROW)),
                NOTHING_RETURNED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                COMPLETED_1,
                NOT_PUBLISHED,
                NOT_PUBLISHED,
                NOT_PUBLISHED);
        row(
                "abandon",
                NO_SETUP,
                plan -> run(() -> plan.abandon(TOMORROW)),
                NOTHING_RETURNED,
                ABANDONED,
                ABANDONED,
                ABANDONED,
                NOT_UNPUBLISHED,
                NOT_UNPUBLISHED,
                NOT_UNPUBLISHED,
                NOT_UNPUBLISHED);
    }

    private static void row(
            String command,
            Consumer<Plan> setup,
            Function<Plan, Object> run,
            BiConsumer<Plan, Object> returned,
            Expect... cells) {
        ROWS.put(command, new Row(setup, run, returned, List.of(cells)));
    }

    static Stream<Arguments> cells() {
        List<Arguments> cells = new ArrayList<>();
        List<String> states = List.copyOf(STATES.keySet());
        ROWS.forEach((command, row) -> {
            for (int i = 0; i < states.size(); i++) {
                cells.add(Arguments.of(states.get(i), command, row.cells().get(i)));
            }
        });
        return cells.stream();
    }

    @ParameterizedTest(name = "{0} × {1} → {2}")
    @MethodSource("cells")
    void GivenEachStateOfAPlan_WhenEachCommandRuns_ThenTheMatrixCellHolds(String state, String command, Expect expect) {
        Plan plan = STATES.get(state).get();
        Row row = ROWS.get(command);

        switch (expect) {
            case Refused refused -> {
                PlanSnapshot before = plan.snapshot();
                assertThatThrownBy(() -> row.command().apply(plan))
                        .isInstanceOfSatisfying(
                                RuleViolation.class,
                                violation -> assertThat(violation.error()).isInstanceOf(refused.error()));
                assertUnchanged(plan, before);
            }
            case NoChange noChange -> {
                PlanSnapshot before = plan.snapshot();
                Object result = row.command().apply(plan);
                assertUnchanged(plan, before);
                row.returned().accept(plan, result);
            }
            case Becomes becomes -> {
                row.setup().accept(plan);
                Object result = row.command().apply(plan);
                assertThat(plan.status()).isEqualTo(becomes.status());
                row.returned().accept(plan, result);
            }
        }
    }

    private static Object run(Runnable command) {
        command.run();
        return null;
    }

    private static void queuedIn(SolveJob job, Lane lane) {
        assertThat(job.snapshot().lane()).isEqualTo(lane);
        assertThat(job.snapshot().phase()).isEqualTo(new SolveJob.Phase.Queued());
    }

    private static void reconciled(Plan plan, Object result) {
        Reconciliation reconciliation = (Reconciliation) result;
        if (plan.status() instanceof Plan.Status.Cancelled) {
            assertThat(reconciliation).isEqualTo(Reconciliation.of(ReconciliationKind.PLAN_CANCELLED));
        } else {
            assertThat(reconciliation.kind()).isEqualTo(ReconciliationKind.MOVED_FROM_ROUTE);
            assertThat(reconciliation.from()).isPresent();
        }
    }

    private static Object moveStop(Plan plan) {
        StopId stop = stop(plan, O1);
        RouteId to = route(plan, V2);
        return run(() -> plan.moveStop(stop, to, 0, judging(plan, builder -> builder.moving(stop, to, 0)), TODAY));
    }

    private static Object takeOff(Plan plan) {
        StopId stop = stop(plan, O3);
        return run(() -> plan.takeOffRoute(stop, judging(plan, builder -> builder.removing(stop)), TODAY));
    }

    private static Object place(Plan plan) {
        StopId stop = StopId.of(uuid(4_242));
        RouteId to = route(plan, V2);
        return run(() -> plan.placeDropped(
                O4, FIRST, to, 0, stop, judging(plan, builder -> builder.inserting(to, 0, stop)), TODAY));
    }

    private static Object pin(Plan plan) {
        return run(() ->
                plan.pin(O1, new PinTarget.ToVehicle(V1), evaluationOf(plan).build(), TODAY));
    }

    private static Object lock(Plan plan) {
        return run(() -> plan.lockRoute(route(plan, V1), RouteLock.WHOLE_ROUTE, TODAY));
    }

    /** An evaluation of the change when the plan has routes; otherwise of the plan as it is. */
    private static Evaluation judging(Plan plan, Consumer<PlanningFixtures.EvaluationBuilder> change) {
        PlanningFixtures.EvaluationBuilder builder = evaluationOf(plan);
        if (!plan.snapshot().routes().isEmpty()) {
            change.accept(builder);
        }
        return builder.build();
    }

    private static RouteId route(Plan plan, VehicleId vehicle) {
        return plan.snapshot().routes().stream()
                .filter(candidate -> candidate.vehicle().equals(vehicle))
                .map(PlanSnapshot.RouteSnapshot::id)
                .findFirst()
                .orElse(RouteId.of(uuid(4_000)));
    }

    private static StopId stop(Plan plan, OrderId order) {
        return plan.snapshot().routes().stream()
                .flatMap(candidate -> candidate.stops().stream())
                .filter(candidate -> candidate.order().equals(order))
                .map(PlanSnapshot.StopSnapshot::id)
                .findFirst()
                .orElse(StopId.of(uuid(4_001)));
    }

    private static ReoptimizationProposal proposal() {
        SolveJob job = aJob().reoptimizing().running();
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(
                List.of(),
                List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                List.of()));
        return job.succeed(PROPOSAL, reoptimizationResult(solution), LATER);
    }
}
