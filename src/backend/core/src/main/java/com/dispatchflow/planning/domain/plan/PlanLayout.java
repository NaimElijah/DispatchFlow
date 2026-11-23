package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A plan's routes, drops and pins as they are, or as a change would leave them, without times: what
 * the optimizer is asked to judge, and what an {@link
 * com.dispatchflow.planning.domain.solution.Evaluation} must describe (ADR-101 point 15, DL §6.3).
 * {@link Plan}'s queries return one and change nothing, so the use case asks the optimizer about the
 * plan after a change before it makes the change, and each command leaves exactly the layout its
 * query returned (ADR-112). It is a read-only view: no command takes one.
 *
 * <p>Its package-private methods derive the layout a change would leave, for those queries; the
 * plan's commands make the same change to its own fields in place, and check they left this layout.
 *
 * @param routes every route, in the plan's order, withdrawn ones included
 * @param dropped the dropped orders, ordered by order ID and attempt
 * @param pins the pins, ordered by order ID
 */
public record PlanLayout(List<LaidOutRoute> routes, List<DroppedOrder> dropped, List<Pin> pins) {

    /** The order a plan keeps its drops in, so undoing an edit restores the same layout. */
    static final Comparator<DroppedOrder> DROP_ORDER = Comparator.comparing(
                    (DroppedOrder drop) -> drop.order().value())
            .thenComparingInt(drop -> drop.attempt().number());

    /** The order a plan keeps its pins in. */
    static final Comparator<Pin> PIN_ORDER =
            Comparator.comparing(pin -> pin.order().value());

    /** Copies the lists. */
    public PlanLayout {
        routes = List.copyOf(routes);
        dropped = List.copyOf(dropped);
        pins = List.copyOf(pins);
    }

    /** The layout with a stop taken off whichever route holds it. */
    PlanLayout removing(StopId stop) {
        return new PlanLayout(
                routes.stream()
                        .map(route -> route.withStops(route.stops().stream()
                                .filter(candidate -> !candidate.stop().equals(stop))
                                .toList()))
                        .toList(),
                dropped,
                pins);
    }

    /** The layout with a stop put on a route at a position, from 0. */
    PlanLayout inserting(RouteId route, int position, LaidOutStop stop) {
        return new PlanLayout(
                routes.stream()
                        .map(candidate -> {
                            if (!candidate.route().equals(route)) {
                                return candidate;
                            }
                            List<LaidOutStop> stops = new ArrayList<>(candidate.stops());
                            stops.add(position, stop);
                            return candidate.withStops(stops);
                        })
                        .toList(),
                dropped,
                pins);
    }

    /** The layout with one more drop, kept in {@link #DROP_ORDER}. */
    PlanLayout adding(DroppedOrder drop) {
        List<DroppedOrder> next = new ArrayList<>(dropped);
        next.add(drop);
        next.sort(DROP_ORDER);
        return new PlanLayout(routes, next, pins);
    }

    /** The layout without an order and attempt's drop. */
    PlanLayout without(OrderId order, Attempt attempt) {
        return new PlanLayout(
                routes,
                dropped.stream()
                        .filter(drop ->
                                !(drop.order().equals(order) && drop.attempt().equals(attempt)))
                        .toList(),
                pins);
    }

    /** The layout with an order's pin replaced, or removed when the target is empty, in {@link #PIN_ORDER}. */
    PlanLayout pinning(OrderId order, Optional<PinTarget> target) {
        List<Pin> next = new ArrayList<>(
                pins.stream().filter(pin -> !pin.order().equals(order)).toList());
        target.ifPresent(pinTarget -> next.add(new Pin(order, pinTarget)));
        next.sort(PIN_ORDER);
        return new PlanLayout(routes, dropped, next);
    }

    /** The layout with a route driven by another driver. */
    PlanLayout drivenBy(RouteId route, AccountId driver) {
        return new PlanLayout(
                routes.stream()
                        .map(candidate -> candidate.route().equals(route)
                                ? new LaidOutRoute(
                                        candidate.route(),
                                        candidate.vehicle(),
                                        driver,
                                        candidate.withdrawn(),
                                        candidate.stops())
                                : candidate)
                        .toList(),
                dropped,
                pins);
    }

    /**
     * One route: the vehicle and driver it is judged with, and its stops in order.
     *
     * @param route the route
     * @param vehicle its vehicle
     * @param driver its driver
     * @param withdrawn whether it was withdrawn mid-route, so it doesn't count under BR-39 (FR-267)
     * @param stops its stops, in order
     */
    public record LaidOutRoute(
            RouteId route, VehicleId vehicle, AccountId driver, boolean withdrawn, List<LaidOutStop> stops) {

        /** Checks every part is present, and copies the stops. */
        public LaidOutRoute {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(driver, "driver");
            stops = List.copyOf(stops);
        }

        private LaidOutRoute withStops(List<LaidOutStop> next) {
            return new LaidOutRoute(route, vehicle, driver, withdrawn, next);
        }
    }

    /**
     * One stop of a route.
     *
     * @param stop the stop
     * @param order its order
     * @param attempt its attempt
     */
    public record LaidOutStop(StopId stop, OrderId order, Attempt attempt) {

        /** Checks every part is present. */
        public LaidOutStop {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(attempt, "attempt");
        }
    }
}
