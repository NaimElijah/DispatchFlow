package com.dispatchflow.planning.domain;

import static com.dispatchflow.planning.domain.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.PlanningFixtures.O4;
import static com.dispatchflow.planning.domain.PlanningFixtures.O5;
import static com.dispatchflow.planning.domain.PlanningFixtures.O6;
import static com.dispatchflow.planning.domain.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.PlanningFixtures.checksFor;
import static com.dispatchflow.planning.domain.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.PlanningFixtures.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Seeded sequences of commands on one plan (DL §14). After every step the invariants are recomputed
 * from the snapshot, in test code: each order and attempt served or dropped once (BR-07), one route
 * per vehicle and driver (BR-39), unique stop IDs, and a snapshot {@code restore} accepts. A refused
 * step leaves the snapshot and the events as they were. The message names the seed and the step, so
 * a failure replays.
 */
class PlanSequenceTest {

    private static final int STEPS = 80;
    private static final List<OrderId> ORDERS = List.of(O1, O2, O3, O4, O5, O6);
    private static final List<String> COMMANDS = List.of(
            "moveStop",
            "moveStop",
            "moveStop",
            "takeOffRoute",
            "placeDropped",
            "pin",
            "unpin",
            "lockRoute",
            "unlockRoute",
            "undo",
            "undo",
            "redo",
            "removeStopOf",
            "recordRouteCheck",
            "publish",
            "handOver",
            "reconcileLateOutcome");

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 7, 42, 2027, 31_337, 99_991})
    void GivenASeededSequenceOfCommands_WhenEachStepRuns_ThenTheInvariantsHoldAfterEveryStep(long seed) {
        Random random = new Random(seed);
        Plan plan = aPlan().ready();
        int applied = 0;
        for (int step = 0; step < STEPS; step++) {
            String command = COMMANDS.get(random.nextInt(COMMANDS.size()));
            String where = "seed " + seed + ", step " + step + ", " + command;
            PlanSnapshot before = plan.snapshot();
            try {
                run(command, plan, random, step);
                plan = reload(plan);
                applied++;
            } catch (RuleViolation | RuleViolations refused) {
                assertThat(plan.snapshot()).as(where).isEqualTo(before);
                assertThat(plan.pendingEvents()).as(where).isEmpty();
                assertThat(plan.pendingRevision()).as(where).isEmpty();
            } catch (RuntimeException | AssertionError unexpected) {
                throw new AssertionError(where + ": " + unexpected, unexpected);
            }
            checkInvariants(plan.snapshot(), where);
        }
        assertThat(applied).as("seed " + seed + " applied some steps").isPositive();
    }

    private static void run(String command, Plan plan, Random random, int step) {
        PlanSnapshot s = plan.snapshot();
        List<StopId> stops = s.routes().stream()
                .flatMap(route -> route.stops().stream())
                .map(PlanSnapshot.StopSnapshot::id)
                .toList();
        List<RouteId> routes =
                s.routes().stream().map(PlanSnapshot.RouteSnapshot::id).toList();
        switch (command) {
            case "moveStop" -> {
                StopId stop = pickOr(stops, random, StopId.of(uuid(9_001)));
                RouteId to = pickOr(routes, random, RouteId.of(uuid(9_002)));
                int position = random.nextInt(4);
                PlanningFixtures.EvaluationBuilder evaluation = evaluationOf(plan);
                if (stops.contains(stop) && routes.contains(to)) {
                    int size = s.routes().stream()
                            .filter(route -> route.id().equals(to))
                            .findFirst()
                            .orElseThrow()
                            .stops()
                            .size();
                    boolean same = s.routes().stream()
                            .anyMatch(route -> route.id().equals(to)
                                    && route.stops().stream()
                                            .anyMatch(
                                                    candidate -> candidate.id().equals(stop)));
                    if (position <= (same ? size - 1 : size)) {
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
                        pickOr(s.dropped(), random, new DroppedOrder(O6, FIRST, new DropReason.Displaced()));
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
                        new PinTarget.ToVehicle(random.nextBoolean() ? V1 : V2),
                        evaluationOf(plan).build(),
                        TODAY);
            case "unpin" -> plan.unpin(pick(ORDERS, random), evaluationOf(plan).build(), TODAY);
            case "lockRoute" ->
                plan.lockRoute(
                        pickOr(routes, random, RouteId.of(uuid(9_005))),
                        random.nextBoolean() ? RouteLock.WHOLE_ROUTE : RouteLock.STOP_SEQUENCE,
                        TODAY);
            case "unlockRoute" -> plan.unlockRoute(pickOr(routes, random, RouteId.of(uuid(9_006))), TODAY);
            case "undo" -> plan.undo(expectedAfter(plan, s.undo(), true), TODAY);
            case "redo" -> plan.redo(expectedAfter(plan, s.redo(), false), TODAY);
            case "removeStopOf" -> plan.removeStopOf(pick(ORDERS, random), FIRST, RemovalCause.ORDER_CANCELLED, LATER);
            case "recordRouteCheck" -> plan.recordRouteCheck(evaluationOf(plan).build());
            case "publish" -> plan.publish(checksFor(plan), DISPATCHER, TODAY, LATER);
            case "handOver" -> {
                AccountId to = random.nextBoolean() ? D3 : D1;
                plan.handOver(
                        pickOr(routes, random, RouteId.of(uuid(9_007))),
                        new DriverStanding(to, true, true),
                        Optional.empty(),
                        false,
                        DISPATCHER,
                        TODAY,
                        LATER);
            }
            default -> {
                StopId stop = pickOr(stops, random, StopId.of(uuid(9_008)));
                OrderId order = s.routes().stream()
                        .flatMap(route -> route.stops().stream())
                        .filter(candidate -> candidate.id().equals(stop))
                        .map(PlanSnapshot.StopSnapshot::order)
                        .findFirst()
                        .orElse(O6);
                plan.reconcileLateOutcome(
                        new LateFact(stop, order, FIRST, pickOr(routes, random, RouteId.of(uuid(9_009)))), LATER);
            }
        }
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

    private static void checkInvariants(PlanSnapshot s, String where) {
        Set<String> keys = new HashSet<>();
        s.routes()
                .forEach(route -> route.stops()
                        .forEach(stop -> assertThat(keys.add(stop.order().value() + "#"
                                        + stop.attempt().number()))
                                .as(where + ": BR-07")
                                .isTrue()));
        s.dropped()
                .forEach(drop -> assertThat(keys.add(
                                drop.order().value() + "#" + drop.attempt().number()))
                        .as(where + ": BR-07")
                        .isTrue());
        Map<VehicleId, RouteId> vehicles = new HashMap<>();
        Map<AccountId, RouteId> drivers = new HashMap<>();
        List<StopId> stopIds = new ArrayList<>();
        for (PlanSnapshot.RouteSnapshot route : s.routes()) {
            route.stops().forEach(stop -> stopIds.add(stop.id()));
            if (route.withdrawal().isEmpty()) {
                assertThat(vehicles.put(route.vehicle(), route.id()))
                        .as(where + ": BR-39 vehicle")
                        .isNull();
                assertThat(drivers.put(route.driver(), route.id()))
                        .as(where + ": BR-39 driver")
                        .isNull();
            }
        }
        assertThat(new HashSet<>(stopIds)).as(where + ": unique stop IDs").hasSameSizeAs(stopIds);
        List<RouteId> routeIds =
                s.routes().stream().map(PlanSnapshot.RouteSnapshot::id).toList();
        List<RouteIndex> indexes =
                s.routes().stream().map(PlanSnapshot.RouteSnapshot::index).toList();
        assertThat(new HashSet<>(routeIds)).as(where + ": unique route IDs").hasSameSizeAs(routeIds);
        assertThat(new HashSet<>(indexes)).as(where + ": unique route indexes").hasSameSizeAs(indexes);
        List<OrderId> pinned = s.pins().stream().map(Pin::order).toList();
        assertThat(new HashSet<>(pinned)).as(where + ": one pin per order").hasSameSizeAs(pinned);
        if (!s.undo().isEmpty() || !s.redo().isEmpty()) {
            assertThat(s.status()).as(where + ": a journal only in ready").isEqualTo(new Plan.Status.Ready());
        }
        assertThatCode(() -> Plan.restore(s)).as(where + ": restorable").doesNotThrowAnyException();
    }

    private static <T> T pick(List<T> items, Random random) {
        return items.get(random.nextInt(items.size()));
    }

    private static <T> T pickOr(List<T> items, Random random, T fallback) {
        return items.isEmpty() || random.nextInt(10) == 0 ? fallback : pick(items, random);
    }
}
