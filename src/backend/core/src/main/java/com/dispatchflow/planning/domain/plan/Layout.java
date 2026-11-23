package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.PlanningError.StaleEvaluation;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.RouteTimes;
import com.dispatchflow.planning.domain.solution.StopTiming;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * A plan's routes, drops and pins as one immutable value. Every {@link Plan} command builds the
 * layout it would leave, checks it, and only then replaces the plan's: so a refused command changes
 * nothing (DL §3.10). Drops and pins are kept sorted, so undoing an edit restores the same layout.
 */
record Layout(List<Route> routes, List<DroppedOrder> dropped, List<Pin> pins) {

    private static final Comparator<DroppedOrder> DROP_ORDER = Comparator.comparing(
                    (DroppedOrder drop) -> drop.order().value())
            .thenComparingInt(drop -> drop.attempt().number());
    private static final Comparator<Pin> PIN_ORDER =
            Comparator.comparing(pin -> pin.order().value());

    Layout {
        routes = List.copyOf(routes);
        dropped = dropped.stream().sorted(DROP_ORDER).toList();
        pins = pins.stream().sorted(PIN_ORDER).toList();
    }

    /** Where a stop is: its route, its position from 0, and the stop. */
    record Located(Route route, int position, PlannedStop stop) {}

    Optional<Route> route(RouteId id) {
        return routes.stream().filter(route -> route.id().equals(id)).findFirst();
    }

    Optional<Located> locate(StopId stop) {
        return locate(candidate -> candidate.id().equals(stop));
    }

    Optional<Located> locate(StopKey key) {
        return locate(candidate -> candidate.key().equals(key));
    }

    private Optional<Located> locate(Predicate<PlannedStop> matches) {
        for (Route route : routes) {
            List<PlannedStop> stops = route.stops();
            for (int position = 0; position < stops.size(); position++) {
                if (matches.test(stops.get(position))) {
                    return Optional.of(new Located(route, position, stops.get(position)));
                }
            }
        }
        return Optional.empty();
    }

    Optional<DroppedOrder> drop(StopKey key) {
        return dropped.stream()
                .filter(drop -> new StopKey(drop.order(), drop.attempt()).equals(key))
                .findFirst();
    }

    Optional<PinTarget> pinOf(OrderId order) {
        return pins.stream()
                .filter(pin -> pin.order().equals(order))
                .map(Pin::target)
                .findFirst();
    }

    /** The layout as the read-only view {@link Plan}'s queries return. */
    PlanLayout view() {
        return new PlanLayout(
                routes.stream()
                        .map(route -> new PlanLayout.LaidOutRoute(
                                route.id(),
                                route.vehicle(),
                                route.driver(),
                                route.withdrawn(),
                                route.stops().stream()
                                        .map(stop ->
                                                new PlanLayout.LaidOutStop(stop.id(), stop.order(), stop.attempt()))
                                        .toList()))
                        .toList(),
                dropped,
                pins);
    }

    /** Every order and attempt the layout serves or drops, routes first, in order. */
    List<StopKey> keys() {
        List<StopKey> keys = new ArrayList<>();
        routes.forEach(route -> keys.addAll(route.keys()));
        dropped.forEach(drop -> keys.add(new StopKey(drop.order(), drop.attempt())));
        return keys;
    }

    Layout withRoutes(List<Route> newRoutes) {
        return new Layout(newRoutes, dropped, pins);
    }

    Layout withDropped(List<DroppedOrder> newDropped) {
        return new Layout(routes, newDropped, pins);
    }

    Layout changing(RouteId id, UnaryOperator<Route> change) {
        return withRoutes(routes.stream()
                .map(route -> route.id().equals(id) ? change.apply(route) : route)
                .toList());
    }

    Layout removing(StopId stop) {
        return withRoutes(routes.stream()
                .map(route -> route.withStops(route.stops().stream()
                        .filter(candidate -> !candidate.id().equals(stop))
                        .toList()))
                .toList());
    }

    Layout inserting(RouteId id, int position, PlannedStop stop) {
        return changing(id, route -> {
            List<PlannedStop> stops = new ArrayList<>(route.stops());
            stops.add(position, stop);
            return route.withStops(stops);
        });
    }

    Layout appending(RouteId id, PlannedStop stop) {
        return changing(id, route -> {
            List<PlannedStop> stops = new ArrayList<>(route.stops());
            stops.add(stop);
            return route.withStops(stops);
        });
    }

    /** Moves a stop: off its route, then into the target at a position of the route without it. */
    Layout moving(PlannedStop stop, RouteId to, int position) {
        return removing(stop.id()).inserting(to, position, stop);
    }

    Layout adding(DroppedOrder drop) {
        List<DroppedOrder> next = new ArrayList<>(dropped);
        next.add(drop);
        return withDropped(next);
    }

    Layout without(StopKey key) {
        return withDropped(dropped.stream()
                .filter(drop -> !new StopKey(drop.order(), drop.attempt()).equals(key))
                .toList());
    }

    Layout pinning(OrderId order, Optional<PinTarget> target) {
        List<Pin> next = new ArrayList<>(
                pins.stream().filter(pin -> !pin.order().equals(order)).toList());
        target.ifPresent(pinTarget -> next.add(new Pin(order, pinTarget)));
        return new Layout(routes, dropped, next);
    }

    Layout locking(RouteId id, Optional<RouteLock> lock) {
        return changing(id, route -> route.withLock(lock));
    }

    /** The layout with no route locked, as publishing leaves it (ADR-112). */
    Layout unlocked() {
        return withRoutes(
                routes.stream().map(route -> route.withLock(Optional.empty())).toList());
    }

    /**
     * Checks that an evaluation describes this layout, and marks every route checked: its routes are
     * exactly these routes, each with its vehicle, its driver and these stops in this order, and its violations name only these
     * routes (ADR-101). With {@code applyTimes}, the routes and stops also take its times (FR-23,
     * BR-68), and a route or stop it couldn't time keeps none (ADR-112).
     *
     * @throws RuleViolation with {@link StaleEvaluation} when it describes another layout
     */
    Layout judgedBy(Evaluation evaluation, Version current, boolean applyTimes) {
        Map<RouteId, RouteTimes> times = new HashMap<>();
        evaluation.routes().forEach(route -> times.put(route.route(), route));
        boolean sameRoutes = times.size() == routes.size()
                && evaluation.routes().size() == routes.size()
                && routes.stream().allMatch(route -> describes(times.get(route.id()), route));
        boolean violationsOnTheseRoutes = evaluation.violations().stream()
                .allMatch(violation -> violation.route().map(times::containsKey).orElse(true));
        if (!sameRoutes || !violationsOnTheseRoutes) {
            throw new RuleViolation(new StaleEvaluation(evaluation.planVersion(), current));
        }
        return withRoutes(routes.stream()
                .map(route -> {
                    Route checked = route.withCheck(CheckState.CHECKED);
                    if (!applyTimes) {
                        return checked;
                    }
                    RouteTimes timed = times.get(route.id());
                    List<PlannedStop> stops = new ArrayList<>();
                    for (int position = 0; position < route.stops().size(); position++) {
                        stops.add(route.stops()
                                .get(position)
                                .withPlan(timed.stops().get(position).plan()));
                    }
                    return checked.withPlan(timed.plan()).withStops(stops);
                })
                .toList());
    }

    /** Whether an evaluation's route is this route: its vehicle, its driver, and its stops in order. */
    private static boolean describes(@Nullable RouteTimes times, Route route) {
        return times != null
                && times.vehicle().equals(route.vehicle())
                && times.driver().equals(route.driver())
                && times.stops().stream().map(StopTiming::stop).toList().equals(route.stopIds());
    }
}
