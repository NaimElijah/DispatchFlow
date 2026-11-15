package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.PositionLog;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.planning.application.spi.ExecutionFacts;
import com.dispatchflow.planning.application.spi.LiveStateFact;
import com.dispatchflow.planning.application.spi.StartedRoutesFact;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;

/**
 * Live Operations' implementation of Planning's {@link ExecutionFacts} spi (DL §2.1's dashed arrow,
 * DL §2.3 rule 2): what has happened on a plan's routes, which Planning asks before it cancels a
 * plan, accepts a proposal or re-optimizes (FR-336, BR-25, BR-82, ADR-050, ADR-101). Planning names
 * its own routes and stops, so this reads only Live Operations' records: the stops' deliveries, the
 * routes' runs and their last positions. It never asks Planning back. A held report counts as no
 * fact until it is confirmed (BR-132). Each {@code LiveStopFact}'s {@code outcomeAt} is the
 * corrected time of the first report of an outcome at the stop (BR-114), which a dispatcher's
 * correction keeps.
 *
 * <p>Built in M9.
 */
public final class ExecutionFactsService implements ExecutionFacts {

    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;
    private final PositionLog positions;

    /**
     * Creates the service.
     *
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     * @param positions where positions are kept
     */
    public ExecutionFactsService(DeliveryRepository deliveries, RouteRunRepository runs, PositionLog positions) {
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
    public StartedRoutesFact startedAmong(List<RouteId> routes, List<StopId> stops) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     */
    @Override
    public List<StopId> changesSince(List<StopId> stops, Instant since) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     */
    @Override
    public LiveStateFact liveStateOf(List<RouteId> routes, List<StopId> stops, Instant now) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
