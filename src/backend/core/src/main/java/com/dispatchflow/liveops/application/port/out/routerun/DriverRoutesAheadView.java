package com.dispatchflow.liveops.application.port.out.routerun;

import com.dispatchflow.liveops.application.port.out.RunState;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;

/**
 * A driver's own routes from today on, each listed with its date, so a route for a later date is
 * found and opened, as the alert of FR-152 opens it (FR-324). It names IDs and dates only; the stops
 * and their details come from the day's view, read per date.
 *
 * <p>To Be Implemented.
 *
 * @param driver the driver, the only account it is read for (BR-41)
 * @param from the first date listed, today in {@code Asia/Jerusalem} (ADR-088)
 * @param routes the driver's routes, by date and then in each plan's order; empty when they have
 *     none
 */
public record DriverRoutesAheadView(AccountId driver, DeliveryDate from, List<DatedRoute> routes) {

    /** Checks every part is present, and copies the routes. */
    public DriverRoutesAheadView {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(from, "from");
        routes = List.copyOf(routes);
    }

    /**
     * One of the driver's routes, with its date.
     *
     * <p>To Be Implemented.
     *
     * @param date the route's delivery date
     * @param route the route
     * @param vehicle its vehicle
     * @param run where its run stands (FR-244)
     */
    public record DatedRoute(DeliveryDate date, RouteId route, VehicleId vehicle, RunState run) {

        /** Checks every part is present. */
        public DatedRoute {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(run, "run");
        }
    }
}
