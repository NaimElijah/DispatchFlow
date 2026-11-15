package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.PositionLog;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.spi.ExecutionFacts;
import com.dispatchflow.planning.application.spi.LiveStateFact;
import com.dispatchflow.planning.application.spi.StartedRoutesFact;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;

/**
 * Live Operations' implementation of Planning's {@link ExecutionFacts} spi (DL §2.1's dashed arrow,
 * DL §2.3 rule 2): what has happened on a date's routes, which Planning asks before it cancels a
 * plan, accepts a proposal or re-optimizes (FR-336, BR-25, BR-82, ADR-050, ADR-101). It reads the
 * date's stops from Planning's {@link PublishedRoutes}, their deliveries, the routes' runs and their
 * last positions, and translates them into Planning's facts. A held report counts as no fact until
 * it is confirmed (BR-132).
 *
 * <p>Built in M9.
 */
public final class ExecutionFactsService implements ExecutionFacts {

    private final PublishedRoutes publishedRoutes;
    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;
    private final PositionLog positions;

    /**
     * Creates the service.
     *
     * @param publishedRoutes Planning's routes in force, for a date's stops
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     * @param positions where positions are kept
     */
    public ExecutionFactsService(
            PublishedRoutes publishedRoutes,
            DeliveryRepository deliveries,
            RouteRunRepository runs,
            PositionLog positions) {
        this.publishedRoutes = publishedRoutes;
        this.deliveries = deliveries;
        this.runs = runs;
        this.positions = positions;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     */
    @Override
    public StartedRoutesFact startedOn(DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     */
    @Override
    public List<StopId> changesSince(DeliveryDate date, Instant since) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     */
    @Override
    public LiveStateFact liveStateOn(DeliveryDate date, Instant now) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
