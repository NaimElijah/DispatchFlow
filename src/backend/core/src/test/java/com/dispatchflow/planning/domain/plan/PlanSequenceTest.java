package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.noChanges;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.roomIn;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.reconciliation.LateFact;
import com.dispatchflow.planning.domain.solution.DropReason;
import com.dispatchflow.planning.domain.solution.DroppedOrder;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.PinTarget;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.SolvedStop;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Seeded sequences of commands on one plan (DL §14): one of the edits a ready plan takes, and one of
 * the changes a published plan takes. After every step the invariants are recomputed from the
 * snapshot, in test code: each order and attempt served or dropped once (BR-07), no order lost except
 * by the command that takes it off, one route per vehicle and driver (BR-39), unique IDs and indexes,
 * and a snapshot {@code restore} accepts. A refused step leaves the snapshot and the events as they
 * were. The message names the seed and the step, so a failure replays. Each command must be applied
 * a minimum number of times across the seeds, so a sequence that only exercises refusals fails.
 */
class PlanSequenceTest {

    private static final long[] SEEDS = {1, 7, 42, 2027, 31_337, 99_991};
    private static final int STEPS = 120;
    private static final int MINIMUM_APPLIED = 10;
    private static final List<OrderId> ORDERS =
            IntStream.rangeClosed(1, 9).mapToObj(n -> OrderId.of(uuid(400 + n))).toList();
    private static final Map<VehicleId, AccountId> FLEET = new LinkedHashMap<>();

    static {
        FLEET.put(V1, D1);
        FLEET.put(V2, D2);
        FLEET.put(V3, D3);
    }

    /** V1 serves the first three orders, V2 two, V3 one; one is displaced, one has no location. */
    private static ValidatedSolution sequenceSolution() {
        return ValidatedSolution.of(ValidationReport.of(
                List.of(),
                List.of(
                        solved(V1, D1, ORDERS.get(0), ORDERS.get(1), ORDERS.get(2)),
                        solved(V2, D2, ORDERS.get(3), ORDERS.get(4)),
                        solved(V3, D3, ORDERS.get(5))),
                List.of(
                        dropped(ORDERS.get(6), new DropReason.Displaced()),
                        dropped(ORDERS.get(7), new DropReason.MissingLocation())),
                List.of()));
    }

    // ---- a ready plan's edits -------------------------------------------------------------------

    private static final List<String> EDITS = List.of(
            "moveStop",
            "moveStop",
            "moveStop",
            "takeOffRoute",
            "takeOffRoute",
            "placeDropped",
            "placeDropped",
            "pin",
            "unpin",
            "lockRoute",
            "unlockRoute",
            "unlockRoute",
            "undo",
            "undo",
            "undo",
            "redo",
            "redo",
            "redo",
            "removeStopOf",
            "removePutBackOrder",
            "recordRouteCheck");

    @Test
    void GivenSeededSequencesOfEditsOnAReadyPlan_WhenEachStepRuns_ThenTheInvariantsHoldAndEveryEditIsApplied() {
        Map<String, Integer> applied = new HashMap<>();
        for (long seed : SEEDS) {
            Random random = new Random(seed);
            Supplier<Plan> fresh = () -> aPlan().solvedAs(sequenceSolution()).ready();
            runSequence(seed, random, fresh, EDITS, PlanSequenceTest::runEdit, applied);
        }
        assertEveryCommandApplied(EDITS, applied);
    }

    private static String runEdit(String command, Plan plan, Random random, int step) {
        PlanSnapshot s = plan.snapshot();
        List<StopId> stops = stopIds(s);
        List<RouteId> routes = routeIds(s);
        switch (command) {
            case "moveStop" -> {
                StopId stop = pickOr(stops, random, StopId.of(uuid(9_001)));
                RouteId to = pickOr(routes, random, RouteId.of(uuid(9_002)));
                int position = random.nextInt(3);
                PlanningFixtures.EvaluationBuilder evaluation = evaluationOf(plan);
                if (stops.contains(stop) && routes.contains(to)) {
                    PlanSnapshot.RouteSnapshot target = route(s, to);
                    boolean same = target.stops().stream()
                            .anyMatch(candidate -> candidate.id().equals(stop));
                    if (position
                            <= (same
                                    ? target.stops().size() - 1
                                    : target.stops().size())) {
                        evaluation.moving(stop, to, position);
                    }
                }
                plan.moveStop(stop, to, position, evaluation.build(), TODAY);
            }
            case "takeOffRoute" -> {
                StopId stop = pickOr(stops, random, StopId.of(uuid(9_003)));
                plan.takeOffRoute(stop, evaluationOf(plan).removing(stop).build(), TODAY);
            }
            case "placeDropped" -> {
                DroppedOrder drop =
                        pickOr(s.dropped(), random, new DroppedOrder(ORDERS.get(8), FIRST, new DropReason.Displaced()));
                RouteId to = pickOr(routes, random, RouteId.of(uuid(9_004)));
                StopId newStop = StopId.of(uuid(20_000 + step));
                PlanningFixtures.EvaluationBuilder evaluation = evaluationOf(plan);
                if (routes.contains(to)) {
                    evaluation.inserting(to, 0, newStop);
                }
                plan.placeDropped(drop.order(), drop.attempt(), to, 0, newStop, evaluation.build(), TODAY);
            }
            case "pin" ->
                plan.pin(
                        pick(ORDERS, random),
                        new PinTarget.ToVehicle(pick(List.copyOf(FLEET.keySet()), random)),
                        evaluationOf(plan).build(),
                        TODAY);
            case "unpin" -> {
                List<OrderId> pinned = s.pins().stream().map(Pin::order).toList();
                plan.unpin(
                        pickOr(pinned, random, ORDERS.get(8)),
                        evaluationOf(plan).build(),
                        TODAY);
            }
            case "lockRoute" ->
                plan.lockRoute(
                        pickOr(routes, random, RouteId.of(uuid(9_005))),
                        random.nextBoolean() ? RouteLock.WHOLE_ROUTE : RouteLock.STOP_SEQUENCE,
                        TODAY);
            case "unlockRoute" -> {
                List<RouteId> locked = s.routes().stream()
                        .filter(route -> route.lock().isPresent())
                        .map(PlanSnapshot.RouteSnapshot::id)
                        .toList();
                plan.unlockRoute(pickOr(locked, random, RouteId.of(uuid(9_006))), TODAY);
            }
            case "undo" -> plan.undo(expectedAfter(plan, s.undo(), true), TODAY);
            case "redo" -> plan.redo(expectedAfter(plan, s.redo(), false), TODAY);
            case "removeStopOf" -> {
                OrderId order = pick(ORDERS, random);
                plan.removeStopOf(order, FIRST, RemovalCause.ORDER_CANCELLED, LATER);
                return key(order);
            }
            case "removePutBackOrder" -> {
                OrderId order =
                        pickOr(s.dropped().stream().map(DroppedOrder::order).toList(), random, ORDERS.get(8));
                plan.removePutBackOrder(order, FIRST);
                return key(order);
            }
            default -> plan.recordRouteCheck(evaluationOf(plan).build());
        }
        return "";
    }

    /** The evaluation of the plan after undoing or redoing its last edit, worked out from the edit. */
    private static Evaluation expectedAfter(Plan plan, List<Edit> journal, boolean undoing) {
        PlanningFixtures.EvaluationBuilder evaluation = evaluationOf(plan);
        if (journal.isEmpty()) {
            return evaluation.build();
        }
        Edit last = journal.get(journal.size() - 1);
        switch (last) {
            case Edit.MovedStop moved ->
                evaluation.moving(
                        moved.stop(),
                        undoing ? moved.fromRoute() : moved.toRoute(),
                        undoing ? moved.fromPosition() : moved.toPosition());
            case Edit.TookOff took -> {
                if (undoing) {
                    evaluation.inserting(took.route(), took.position(), took.stop());
                } else {
                    evaluation.removing(took.stop());
                }
            }
            case Edit.Placed placed -> {
                if (undoing) {
                    evaluation.removing(placed.stop());
                } else {
                    evaluation.inserting(placed.route(), placed.position(), placed.stop());
                }
            }
            default -> {
                // Pins and locks change no route, so the plan's own routes describe it.
            }
        }
        return evaluation.build();
    }

    // ---- a published plan's changes -------------------------------------------------------------

    private static final List<String> CHANGES = List.of(
            "accept",
            "accept",
            "accept",
            "accept",
            "handOver",
            "handOver",
            "removeStopOf",
            "reconcileLateOutcome",
            "reconcileLateOutcome",
            "recordRouteCheck",
            "cancel");

    @Test
    void GivenSeededSequencesOfChangesOnAPublishedPlan_WhenEachStepRuns_ThenTheInvariantsHoldAndEveryChangeIsApplied() {
        Map<String, Integer> applied = new HashMap<>();
        for (long seed : SEEDS) {
            Random random = new Random(seed);
            Supplier<Plan> fresh = () -> aPlan().solvedAs(sequenceSolution()).published();
            runSequence(seed, random, fresh, CHANGES, PlanSequenceTest::runChange, applied);
        }
        assertEveryCommandApplied(CHANGES, applied);
    }

    private static String runChange(String command, Plan plan, Random random, int step) {
        PlanSnapshot s = plan.snapshot();
        List<RouteId> routes = routeIds(s);
        Instant now = LATER.plusSeconds(step);
        switch (command) {
            case "accept" ->
                plan.accept(
                        proposalFor(plan, random, step),
                        noChanges(V1, V2, V3),
                        freshIds(1_000L * (step + 1)),
                        DISPATCHER,
                        TODAY,
                        now);
            case "handOver" -> {
                AccountId to = pick(List.of(D1, D2, D3, AccountId.of(uuid(250 + step))), random);
                plan.handOver(
                        pickOr(routes, random, RouteId.of(uuid(9_007))),
                        new DriverStanding(to, true, true),
                        Optional.empty(),
                        false,
                        DISPATCHER,
                        TODAY,
                        now);
            }
            case "removeStopOf" -> {
                OrderId order = pick(ORDERS, random);
                plan.removeStopOf(order, FIRST, RemovalCause.ORDER_RESCHEDULED, now);
                return key(order);
            }
            case "recordRouteCheck" -> plan.recordRouteCheck(evaluationOf(plan).build());
            case "cancel" -> {
                StartedRoutes started = random.nextInt(3) == 0
                        ? StartedRoutes.none()
                        : new StartedRoutes(List.of(pickOr(routes, random, RouteId.of(uuid(9_010)))), List.of());
                plan.cancel(started, TODAY, now);
            }
            default -> {
                List<StopId> stops = stopIds(s);
                StopId stop = pickOr(stops, random, StopId.of(uuid(30_000 + step)));
                OrderId order = s.routes().stream()
                        .flatMap(route -> route.stops().stream())
                        .filter(candidate -> candidate.id().equals(stop))
                        .map(PlanSnapshot.StopSnapshot::order)
                        .findFirst()
                        .orElse(ORDERS.get(8));
                plan.reconcileLateOutcome(
                        new LateFact(stop, order, FIRST, pickOr(routes, random, RouteId.of(uuid(9_009)))), now);
            }
        }
        return "";
    }

    /**
     * A proposal as the optimizer would make it for the plan's current revision: each active route
     * may keep a reported prefix off every route (BR-10), the remaining stops are dealt to the active
     * vehicles, a vehicle may be withdrawn with no stops (BR-108), and every drop is dropped again.
     * One in eight leaves a drop out, so acceptance must refuse it.
     */
    private static ReoptimizationProposal proposalFor(Plan plan, Random random, int step) {
        PlanSnapshot s = plan.snapshot();
        RevisionNo revision = ((Plan.Status.Published) s.status()).revision();
        Map<VehicleId, AccountId> drivers = new LinkedHashMap<>();
        List<SolvedStopKey> toDeal = new ArrayList<>();
        for (PlanSnapshot.RouteSnapshot route : s.routes()) {
            if (route.withdrawal().isPresent()) {
                continue;
            }
            drivers.put(route.vehicle(), route.driver());
            int reported = random.nextInt(route.stops().size() + 1) / 2;
            route.stops().stream()
                    .skip(reported)
                    .forEach(stop -> toDeal.add(new SolvedStopKey(stop.order(), stop.attempt())));
        }
        Optional<VehicleId> withdrawn = drivers.size() > 1 && random.nextInt(4) == 0
                ? Optional.of(pick(List.copyOf(drivers.keySet()), random))
                : Optional.empty();
        List<VehicleId> serving = drivers.keySet().stream()
                .filter(vehicle -> withdrawn.map(out -> !out.equals(vehicle)).orElse(true))
                .toList();
        Collections.shuffle(toDeal, random);
        Map<VehicleId, List<SolvedStop>> dealt = new LinkedHashMap<>();
        drivers.keySet().forEach(vehicle -> dealt.put(vehicle, new ArrayList<>()));
        List<DroppedOrder> drops = new ArrayList<>(s.dropped());
        for (SolvedStopKey key : toDeal) {
            if (random.nextInt(6) == 0) {
                drops.add(new DroppedOrder(key.order(), key.attempt(), new DropReason.Displaced()));
            } else {
                List<SolvedStop> stops = dealt.get(pick(serving, random));
                stops.add(new SolvedStop(
                        key.order(), key.attempt(), PlanningFixtures.stopPlan(30_000 + 600L * stops.size())));
            }
        }
        if (!drops.isEmpty() && random.nextInt(8) == 0) {
            drops.remove(0);
        }
        List<SolvedRoute> routes = new ArrayList<>();
        dealt.forEach((vehicle, stops) ->
                routes.add(new SolvedRoute(vehicle, drivers.get(vehicle), PlanningFixtures.routePlan(50_000), stops)));
        VehicleWithdrawal[] withdrawals = withdrawn
                .map(vehicle ->
                        new VehicleWithdrawal[] {new VehicleWithdrawal(vehicle, WithdrawalCause.VEHICLE_UNAVAILABLE)})
                .orElse(new VehicleWithdrawal[0]);
        SolveJob job = SolveJob.queue(
                JobId.of(uuid(40_000 + step)),
                PlanningFixtures.PLAN,
                Lane.REOPTIMIZATION,
                SolverName.OR_TOOLS,
                SolveLimit.REOPTIMIZATION,
                Seed.of(step),
                Optional.of(revision),
                roomIn(Lane.REOPTIMIZATION),
                LATER);
        job.claim(LATER);
        job.searchStarted(LATER);
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, List.of()));
        return job.succeed(ProposalId.of(uuid(50_000 + step)), reoptimizationResult(solution, withdrawals), LATER);
    }

    /** An order and attempt a proposal is dealt. */
    private record SolvedStopKey(OrderId order, Attempt attempt) {}

    // ---- running a sequence ---------------------------------------------------------------------

    /** One step: runs a command and names the order and attempt it may take off, or "" for none. */
    @FunctionalInterface
    private interface Step {
        String run(String command, Plan plan, Random random, int step);
    }

    private static void runSequence(
            long seed,
            Random random,
            Supplier<Plan> fresh,
            List<String> commands,
            Step step,
            Map<String, Integer> applied) {
        Plan plan = fresh.get();
        for (int n = 0; n < STEPS; n++) {
            String command = commands.get(random.nextInt(commands.size()));
            String where = "seed " + seed + ", step " + n + ", " + command;
            PlanSnapshot before = plan.snapshot();
            try {
                String takenOff = step.run(command, plan, random, n);
                checkNothingLost(before, plan.snapshot(), takenOff, where);
                plan = reload(plan);
                applied.merge(command, 1, Integer::sum);
            } catch (RuleViolation | RuleViolations refused) {
                assertThat(plan.snapshot()).as(where).isEqualTo(before);
                assertThat(plan.pendingEvents()).as(where).isEmpty();
                assertThat(plan.pendingRevision()).as(where).isEmpty();
            } catch (RuntimeException | AssertionError unexpected) {
                throw new AssertionError(where + ": " + unexpected, unexpected);
            }
            checkInvariants(plan.snapshot(), where);
            if (plan.status() instanceof Plan.Status.Cancelled) {
                plan = fresh.get();
            }
        }
    }

    private static void assertEveryCommandApplied(List<String> commands, Map<String, Integer> applied) {
        Stream.of(commands.toArray(String[]::new))
                .distinct()
                .forEach(command -> assertThat(applied.getOrDefault(command, 0))
                        .as(command + " applied across the seeds, of " + applied)
                        .isGreaterThanOrEqualTo(MINIMUM_APPLIED));
    }

    /** No order and attempt the plan held is lost, except the one the command takes off (BR-07). */
    private static void checkNothingLost(PlanSnapshot before, PlanSnapshot after, String takenOff, String where) {
        Set<String> held = new HashSet<>(keys(after));
        for (String key : keys(before)) {
            if (!key.equals(takenOff)) {
                assertThat(held).as(where + ": BR-07, nothing lost").contains(key);
            }
        }
    }

    private static void checkInvariants(PlanSnapshot s, String where) {
        List<String> keys = keys(s);
        assertThat(new HashSet<>(keys)).as(where + ": BR-07").hasSameSizeAs(keys);
        Map<VehicleId, RouteId> vehicles = new HashMap<>();
        Map<AccountId, RouteId> drivers = new HashMap<>();
        for (PlanSnapshot.RouteSnapshot route : s.routes()) {
            if (route.withdrawal().isEmpty()) {
                assertThat(vehicles.put(route.vehicle(), route.id()))
                        .as(where + ": BR-39 vehicle")
                        .isNull();
                assertThat(drivers.put(route.driver(), route.id()))
                        .as(where + ": BR-39 driver")
                        .isNull();
            }
        }
        List<StopId> stopIds = stopIds(s);
        assertThat(new HashSet<>(stopIds)).as(where + ": unique stop IDs").hasSameSizeAs(stopIds);
        List<RouteId> routeIds = routeIds(s);
        List<RouteIndex> indexes =
                s.routes().stream().map(PlanSnapshot.RouteSnapshot::index).toList();
        assertThat(new HashSet<>(routeIds)).as(where + ": unique route IDs").hasSameSizeAs(routeIds);
        assertThat(new HashSet<>(indexes)).as(where + ": unique route indexes").hasSameSizeAs(indexes);
        List<OrderId> pinned = s.pins().stream().map(Pin::order).toList();
        assertThat(new HashSet<>(pinned)).as(where + ": one pin per order").hasSameSizeAs(pinned);
        if (!s.undo().isEmpty() || !s.redo().isEmpty()) {
            assertThat(s.status()).as(where + ": a journal only in ready").isEqualTo(new Plan.Status.Ready());
        }
        if (!(s.status() instanceof Plan.Status.Ready)) {
            assertThat(s.routes())
                    .as(where + ": no lock once published")
                    .allSatisfy(route -> assertThat(route.lock()).isEmpty());
        }
        assertThatCode(() -> Plan.restore(s)).as(where + ": restorable").doesNotThrowAnyException();
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static List<String> keys(PlanSnapshot s) {
        List<String> keys = new ArrayList<>();
        s.routes().forEach(route -> route.stops().forEach(stop -> keys.add(key(stop.order(), stop.attempt()))));
        s.dropped().forEach(drop -> keys.add(key(drop.order(), drop.attempt())));
        return keys;
    }

    private static String key(OrderId order) {
        return key(order, FIRST);
    }

    private static String key(OrderId order, Attempt attempt) {
        return order.value() + "#" + attempt.number();
    }

    private static List<StopId> stopIds(PlanSnapshot s) {
        return s.routes().stream()
                .flatMap(route -> route.stops().stream())
                .map(PlanSnapshot.StopSnapshot::id)
                .toList();
    }

    private static List<RouteId> routeIds(PlanSnapshot s) {
        return s.routes().stream().map(PlanSnapshot.RouteSnapshot::id).toList();
    }

    private static PlanSnapshot.RouteSnapshot route(PlanSnapshot s, RouteId id) {
        return s.routes().stream()
                .filter(route -> route.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static <T> T pick(List<T> items, Random random) {
        return items.get(random.nextInt(items.size()));
    }

    private static <T> T pickOr(List<T> items, Random random, T fallback) {
        return items.isEmpty() || random.nextInt(10) == 0 ? fallback : pick(items, random);
    }
}
