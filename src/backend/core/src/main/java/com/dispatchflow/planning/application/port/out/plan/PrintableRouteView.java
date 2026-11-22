package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.orders.application.api.StopOrderFact;
import com.dispatchflow.planning.application.port.out.RouteView;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.List;
import java.util.Objects;

/**
 * One route to print or export: its stops in order, each with its address, phones, notes, weight and
 * volume (FR-72). The route's own part is Planning's; each stop's details are Customers &amp; Orders',
 * read through {@code StopOrders} and {@code OrdersForDate} when it is printed.
 *
 * <p>Built in an unscheduled milestone.
 *
 * @param route the route, its label, vehicle, driver and planned times (FR-148)
 * @param stops its stops' details, in the route's order
 */
public record PrintableRouteView(RouteView route, List<Stop> stops) {

    /** Checks the route is present, and copies the stops. */
    public PrintableRouteView {
        Objects.requireNonNull(route, "route");
        stops = List.copyOf(stops);
    }

    /**
     * One stop's printed details. Its address, phones and notes are a person's data, so it prints
     * redacted.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param stop the stop
     * @param order the order, its ID included, with its address, phones and notes as a driver sees
     *     them (FR-86, FR-87, FR-135)
     * @param weight the order's weight
     * @param volume the order's volume
     */
    public record Stop(StopId stop, StopOrderFact order, Weight weight, Volume volume) implements PersonalData {

        /** Checks every part is present. */
        public Stop {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(weight, "weight");
            Objects.requireNonNull(volume, "volume");
        }

        /**
         * A form that hides the order's details.
         *
         * @return {@code Stop[redacted]}
         */
        @Override
        public String toString() {
            return "Stop[redacted]";
        }
    }
}
