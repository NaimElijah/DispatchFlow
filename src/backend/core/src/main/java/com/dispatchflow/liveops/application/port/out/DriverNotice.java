package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import java.util.Objects;

/**
 * What a push to a driver says: only that something changed, never personal data, so the app
 * fetches the details with its own token (NFR-218, ADR-026, ADR-083).
 *
 * <p>To Be Implemented.
 */
public sealed interface DriverNotice {

    /**
     * The driver's routes of a date are ready, on publishing (FR-152, FR-324).
     *
     * <p>To Be Implemented.
     *
     * @param date the plan's date, which the app opens
     */
    record RoutesReady(DeliveryDate date) implements DriverNotice {

        /** Checks the date is present. */
        public RoutesReady {
            Objects.requireNonNull(date, "date");
        }
    }

    /**
     * A route of the driver's changed (FR-71, FR-97, FR-249).
     *
     * <p>To Be Implemented.
     *
     * @param route the route
     * @param revision the plan's revision that changed it, 1 or more, which the driver confirms
     */
    record RouteChanged(RouteId route, int revision) implements DriverNotice {

        /** Checks the route is present. */
        public RouteChanged {
            Objects.requireNonNull(route, "route");
        }
    }

    /**
     * The plan of a date the driver had a route on was cancelled (FR-337).
     *
     * <p>To Be Implemented.
     *
     * @param date the cancelled plan's date
     */
    record PlanCancelled(DeliveryDate date) implements DriverNotice {

        /** Checks the date is present. */
        public PlanCancelled {
            Objects.requireNonNull(date, "date");
        }
    }
}
