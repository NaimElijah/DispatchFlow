package com.dispatchflow.liveops.application.port.out.routerun;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A date's stops at risk of missing their window, which the dispatcher's list and the driver's list
 * mark (FR-34, FR-251, FR-253). A leave-at-door stop is never at risk (FR-369).
 *
 * <p>To Be Implemented.
 *
 * @param date the date
 * @param at the time the projections were made at
 * @param stops the stops at risk, in route and plan order
 */
public record AtRiskView(DeliveryDate date, Instant at, List<StopAtRisk> stops) {

    /** Checks every part is present, and copies the stops. */
    public AtRiskView {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(at, "at");
        stops = List.copyOf(stops);
    }

    /**
     * One stop at risk.
     *
     * <p>To Be Implemented.
     *
     * @param stop the stop
     * @param route its route
     * @param order its order
     * @param projectedArrival when it is now expected to be reached (FR-254)
     * @param windowEnd when its window ends, which the projection is later than less 15 minutes
     *     (FR-253)
     */
    public record StopAtRisk(
            StopId stop, RouteId route, OrderId order, Instant projectedArrival, IsraelDateTime windowEnd) {

        /** Checks every part is present. */
        public StopAtRisk {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(projectedArrival, "projectedArrival");
            Objects.requireNonNull(windowEnd, "windowEnd");
        }
    }
}
