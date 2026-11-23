package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;

/**
 * A plan's routes, drops and pins as they are, or as a change would leave them, without times: what
 * the optimizer is asked to judge, and what an {@link
 * com.dispatchflow.planning.domain.solution.Evaluation} must describe (ADR-101 point 15, DL §6.3).
 * {@link Plan}'s queries return one and change nothing, so the use case asks the optimizer about the
 * plan after a change before it makes the change, and each command leaves exactly the layout its
 * query returned (ADR-112). It is a read-only view: no command takes one.
 *
 * @param routes every route, in the plan's order, withdrawn ones included
 * @param dropped the dropped orders, ordered by order ID and attempt
 * @param pins the pins, ordered by order ID
 */
public record PlanLayout(List<LaidOutRoute> routes, List<DroppedOrder> dropped, List<Pin> pins) {

    /** Copies the lists. */
    public PlanLayout {
        routes = List.copyOf(routes);
        dropped = List.copyOf(dropped);
        pins = List.copyOf(pins);
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
