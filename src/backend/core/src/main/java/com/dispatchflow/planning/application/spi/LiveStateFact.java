package com.dispatchflow.planning.application.spi;

import com.dispatchflow.shared.RouteId;
import java.util.List;

/**
 * What has happened on a date's routes, as a re-optimization takes it (ADR-050 points 1 to 3,
 * ADR-101): the facts at each reported stop, the finished routes, and each route's last positions.
 * Planning's {@code SolveRequestAssembler} sends them raw, and the optimizer applies every
 * convention.
 *
 * <p>Built in M9.
 *
 * @param stops every reported stop of the date, each once; a stop left out is pending
 * @param finishedRoutes the routes whose run has finished, which get nothing more (BR-117)
 * @param positions the last positions of each route that has any
 */
public record LiveStateFact(
        List<LiveStopFact> stops, List<RouteId> finishedRoutes, List<RoutePositionsFact> positions) {

    /** Copies the lists. */
    public LiveStateFact {
        stops = List.copyOf(stops);
        finishedRoutes = List.copyOf(finishedRoutes);
        positions = List.copyOf(positions);
    }
}
