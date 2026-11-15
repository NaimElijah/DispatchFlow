package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.liveops.application.api.ArrivalProjections;
import com.dispatchflow.liveops.application.api.ProgressFact;
import com.dispatchflow.liveops.application.api.StopArrivalFact;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Live Operations' implementation of {@link ArrivalProjections} (DL §10.4, ADR-067, ADR-114). It
 * builds each route's {@code PlannedLegs} from Planning's {@link PublishedRoutes} alone: each stop
 * carries its planned leg and the window, service time and door flag its revision took it with, so
 * a stop whose order was since put back, cancelled after it was served, or rescheduled still gets
 * its leg. It takes each stop's {@code StopFact} from its delivery and the run's start, and asks the
 * domain's {@code ArrivalProjection}, then translates its answer into {@link StopArrivalFact}s (DL
 * §2.3 rule 1).
 *
 * <p>{@link #of(StopId, Instant)} finds the stop's route through {@code PublishedRoutes.routeOf},
 * and answers empty when it finds none. {@link #progressOf} reads only the route's run and the stop's
 * delivery, and never asks Planning.
 *
 * <p>Built in M8.
 */
public final class ArrivalProjectionsService implements ArrivalProjections {

    private final PublishedRoutes publishedRoutes;
    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;

    /**
     * Creates the service.
     *
     * @param publishedRoutes Planning's routes in force, their stored legs and their stops' windows,
     *     service times and door flags
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     */
    public ArrivalProjectionsService(
            PublishedRoutes publishedRoutes, DeliveryRepository deliveries, RouteRunRepository runs) {
        this.publishedRoutes = publishedRoutes;
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
    public Optional<StopArrivalFact> of(StopId stop, Instant now) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M8.
     */
    @Override
    public ProgressFact progressOf(RouteId route, StopId stop) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
