package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.liveops.application.api.ArrivalProjections;
import com.dispatchflow.liveops.application.api.StopArrivalFact;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;

/**
 * Live Operations' implementation of {@link ArrivalProjections} (DL §10.4, ADR-067, ADR-114). It
 * builds each route's {@code PlannedLegs} from Planning's {@link PublishedRoutes} and the orders'
 * windows and door flags from Customers &amp; Orders' {@link OrdersForDate}, takes each stop's {@code
 * StopFact} from its delivery and the run's start, and asks the domain's {@code ArrivalProjection},
 * then translates its answer into {@link StopArrivalFact}s (DL §2.3 rule 1).
 *
 * <p>Planning's API has no lookup from a stop to its route yet, so {@link #of(StopId, Instant)}
 * waits for one.
 *
 * <p>Built in M8.
 */
public final class ArrivalProjectionsService implements ArrivalProjections {

    private final PublishedRoutes publishedRoutes;
    private final OrdersForDate ordersForDate;
    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;

    /**
     * Creates the service.
     *
     * @param publishedRoutes Planning's routes in force and their stored legs
     * @param ordersForDate Customers &amp; Orders' windows and door flags
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     */
    public ArrivalProjectionsService(
            PublishedRoutes publishedRoutes,
            OrdersForDate ordersForDate,
            DeliveryRepository deliveries,
            RouteRunRepository runs) {
        this.publishedRoutes = publishedRoutes;
        this.ordersForDate = ordersForDate;
        this.deliveries = deliveries;
        this.runs = runs;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M8.
     */
    @Override
    public List<StopArrivalFact> projectedOn(DeliveryDate date, Instant now) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M8.
     */
    @Override
    public StopArrivalFact of(StopId stop, Instant now) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
