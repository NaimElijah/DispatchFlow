package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The invariants a plan holds at rest, checked in one place over the plan's own fields (DL §4.4,
 * ADR-101). The plan's load check refuses a stored state that breaks one, and every command checks
 * the state it left, so a command can never leave a state that would not load again (ADR-126
 * decisions 8 and 11).
 */
final class PlanInvariants {

    private PlanInvariants() {}

    /**
     * The first invariant a plan breaks.
     *
     * @param plan the plan, as loaded or as a command left it
     * @return the broken rule, with no stored value in it; empty when the plan holds every one
     * @throws com.dispatchflow.shared.StoredStateInvalid when a stored value it reads is refused by
     *     its own rules
     */
    static Optional<String> brokenBy(Plan plan) {
        List<Route> routes = plan.routes();
        List<DroppedOrder> dropped = plan.dropped();
        List<Edit> undo = plan.undo();
        List<Edit> redo = plan.redo();
        Optional<String> state = stateFields(plan, routes, dropped, undo, redo);
        if (state.isPresent()) {
            return state;
        }
        List<StopKey> keys = new ArrayList<>();
        routes.forEach(route -> keys.addAll(route.keys()));
        dropped.forEach(drop -> keys.add(new StopKey(drop.order(), drop.attempt())));
        if (new HashSet<>(keys).size() != keys.size()) {
            return Optional.of("an order and attempt is served or dropped at most once");
        }
        Set<VehicleId> vehicles = new HashSet<>();
        Set<AccountId> drivers = new HashSet<>();
        for (Route route : routes) {
            if (!route.withdrawn() && (!vehicles.add(route.vehicle()) || !drivers.add(route.driver()))) {
                return Optional.of("a vehicle or driver drives at most one route");
            }
        }
        List<RouteId> routeIds = routes.stream().map(Route::id).toList();
        List<StopId> stopIds =
                routes.stream().flatMap(route -> route.stopIds().stream()).toList();
        List<RouteIndex> indexes = routes.stream().map(Route::index).toList();
        if (new HashSet<>(routeIds).size() != routeIds.size()
                || new HashSet<>(stopIds).size() != stopIds.size()
                || new HashSet<>(indexes).size() != indexes.size()) {
            return Optional.of("route IDs, stop IDs and route indexes are unique");
        }
        List<Pin> pins = plan.pins();
        if (pins.stream().map(Pin::order).distinct().count() != pins.size()) {
            return Optional.of("an order has at most one pin");
        }
        Set<RouteId> known = new HashSet<>(routeIds);
        boolean violationsKnown = plan.violations().stream()
                .allMatch(violation -> violation.route().map(known::contains).orElse(true));
        boolean editsKnown = Stream.concat(undo.stream(), redo.stream())
                .allMatch(edit -> routesOf(edit).stream().allMatch(known::contains));
        if (!violationsKnown || !editsKnown) {
            return Optional.of("violations and edits name only routes of the plan");
        }
        Optional<String> tops = journalTops(routes, dropped, undo, redo, new HashSet<>(stopIds));
        if (tops.isPresent()) {
            return tops;
        }
        // The plan's own collection creates and deletes its stops, so it must hold each stop on a
        // route, and no other; on load the migration's foreign key already keeps it so.
        Set<PlannedStop> onRoutes = new HashSet<>();
        routes.forEach(route -> onRoutes.addAll(route.stops()));
        if (!onRoutes.equals(plan.keptStops())) {
            return Optional.of("the plan keeps exactly the stops on its routes");
        }
        return Optional.empty();
    }

    private static Optional<String> stateFields(
            Plan plan, List<Route> routes, List<DroppedOrder> dropped, List<Edit> undo, List<Edit> redo) {
        List<Violation> violations = plan.violations();
        boolean hasLayout =
                !routes.isEmpty() || !dropped.isEmpty() || !plan.excluded().isEmpty();
        boolean hasJournal = !undo.isEmpty() || !redo.isEmpty();
        Optional<AppliedSolve> solved = plan.applied();
        boolean applied = solved.isPresent();
        Plan.Status status = plan.status();
        boolean afterPublishing = status instanceof Plan.Status.Published
                || status instanceof Plan.Status.Completed
                || status instanceof Plan.Status.Cancelled;
        switch (status) {
            case Plan.Status.Draft draft -> {
                if (hasLayout || applied || hasJournal || !violations.isEmpty()) {
                    return Optional.of(
                            "a draft plan has no routes, drops, excluded vehicles, solve, violations or journal");
                }
            }
            case Plan.Status.Solving solving -> {
                if (hasLayout && !applied) {
                    return Optional.of("a solving plan has routes only from an applied solve");
                }
                boolean waitsForApplied = solved.map(done ->
                                done.basis().job().equals(solving.pending().job()))
                        .orElse(false);
                if (waitsForApplied) {
                    return Optional.of("a solving plan waits for a job it has not applied");
                }
                if (hasJournal && !applied) {
                    return Optional.of("a journal belongs to a plan solved before");
                }
                if (!applied && !violations.isEmpty()) {
                    return Optional.of("a plan never solved holds no violations");
                }
            }
            case Plan.Status.Ready ready -> {
                if (!applied) {
                    return Optional.of("a ready plan has an applied solve");
                }
            }
            default -> {
                if (hasJournal) {
                    return Optional.of("only a ready or solving plan keeps a journal");
                }
                if (afterPublishing && !applied) {
                    return Optional.of("a published plan has an applied solve");
                }
            }
        }
        boolean factsOnRoutes = routes.stream()
                .anyMatch(route -> route.withdrawn()
                        || route.confirmedReturn().isPresent()
                        || route.stops().stream().anyMatch(stop -> stop.late().isPresent()));
        if (factsOnRoutes && !afterPublishing) {
            return Optional.of("withdrawals, late outcomes and confirmed returns belong to a published plan");
        }
        boolean locked = routes.stream().anyMatch(route -> route.lock().isPresent());
        if (locked && afterPublishing) {
            return Optional.of("locks belong to a plan not yet published");
        }
        if (undo.size() + redo.size() > Plan.JOURNAL_LIMIT) {
            return Optional.of("a journal holds at most " + Plan.JOURNAL_LIMIT + " edits");
        }
        boolean appliedEarly = solved.map(
                        done -> done.appliedAt().isBefore(done.basis().requestedAt()))
                .orElse(false);
        if (appliedEarly) {
            return Optional.of("a solve is applied no earlier than it was requested");
        }
        return Optional.empty();
    }

    /**
     * Each journal's last entry names its stop where the entry left it, on its route at its position,
     * and the position undoing or redoing it returns the stop to is in range. Only the last entries
     * are determined: an earlier edit's stop may have been moved, taken off or placed again since.
     */
    private static Optional<String> journalTops(
            List<Route> routes, List<DroppedOrder> drops, List<Edit> undo, List<Edit> redo, Set<StopId> present) {
        Map<RouteId, List<StopId>> stopsOf = new HashMap<>();
        routes.forEach(route -> stopsOf.put(route.id(), route.stopIds()));
        Set<StopKey> dropped = new HashSet<>();
        drops.forEach(drop -> dropped.add(new StopKey(drop.order(), drop.attempt())));
        boolean undoHolds = undo.isEmpty() || leftAsDone(undo.get(undo.size() - 1), stopsOf, present, dropped);
        boolean redoHolds = redo.isEmpty() || leftAsUndone(redo.get(redo.size() - 1), stopsOf, present, dropped);
        if (!undoHolds || !redoHolds) {
            return Optional.of("the last journal entries name stops where they left them");
        }
        return Optional.empty();
    }

    /** The undo stack's last entry: the plan is as the edit left it, and undoing it fits. */
    private static boolean leftAsDone(
            Edit edit, Map<RouteId, List<StopId>> routes, Set<StopId> present, Set<StopKey> dropped) {
        return switch (edit) {
            case Edit.MovedStop moved ->
                at(routes, moved.toRoute(), moved.toPosition(), moved.stop())
                        && fitsAfterLeaving(routes, moved.toRoute(), moved.fromRoute(), moved.fromPosition());
            case Edit.TookOff took ->
                !present.contains(took.stop())
                        && dropped.contains(new StopKey(took.order(), took.attempt()))
                        && fits(routes, took.route(), took.position());
            case Edit.Placed placed -> at(routes, placed.route(), placed.position(), placed.stop());
            default -> true;
        };
    }

    /** The redo stack's last entry: the plan is as undoing the edit left it, and redoing it fits. */
    private static boolean leftAsUndone(
            Edit edit, Map<RouteId, List<StopId>> routes, Set<StopId> present, Set<StopKey> dropped) {
        return switch (edit) {
            case Edit.MovedStop moved ->
                at(routes, moved.fromRoute(), moved.fromPosition(), moved.stop())
                        && fitsAfterLeaving(routes, moved.fromRoute(), moved.toRoute(), moved.toPosition());
            case Edit.TookOff took -> at(routes, took.route(), took.position(), took.stop());
            case Edit.Placed placed ->
                !present.contains(placed.stop())
                        && dropped.contains(new StopKey(placed.order(), placed.attempt()))
                        && fits(routes, placed.route(), placed.position());
            default -> true;
        };
    }

    /** Whether a stop is on a route at a position. */
    private static boolean at(Map<RouteId, List<StopId>> routes, RouteId route, int position, StopId stop) {
        List<StopId> stops = routes.get(route);
        return stops != null
                && position >= 0
                && position < stops.size()
                && stops.get(position).equals(stop);
    }

    /** Whether a stop can be inserted into a route at a position. */
    private static boolean fits(Map<RouteId, List<StopId>> routes, RouteId route, int position) {
        List<StopId> stops = routes.get(route);
        return stops != null && position >= 0 && position <= stops.size();
    }

    /** Whether a stop leaving one route can be inserted into another, or the same, at a position. */
    private static boolean fitsAfterLeaving(
            Map<RouteId, List<StopId>> routes, RouteId leaving, RouteId into, int position) {
        List<StopId> stops = routes.get(into);
        int size = stops == null ? -1 : stops.size() - (leaving.equals(into) ? 1 : 0);
        return stops != null && position >= 0 && position <= size;
    }

    private static List<RouteId> routesOf(Edit edit) {
        return switch (edit) {
            case Edit.MovedStop moved -> List.of(moved.fromRoute(), moved.toRoute());
            case Edit.TookOff took -> List.of(took.route());
            case Edit.Placed placed -> List.of(placed.route());
            case Edit.Pinned pinned -> List.of();
            case Edit.Unpinned unpinned -> List.of();
            case Edit.Locked locked -> List.of(locked.route());
            case Edit.Unlocked unlocked -> List.of(unlocked.route());
        };
    }
}
