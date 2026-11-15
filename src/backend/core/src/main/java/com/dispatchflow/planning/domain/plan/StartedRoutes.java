package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.List;

/**
 * What has happened on a published plan's routes, from Live Operations' {@code ExecutionFacts}, which
 * cancelling the plan judges (FR-336). A stop recorded on a driver's behalf starts no route, so both
 * lists count.
 *
 * @param started the routes that have started
 * @param reported the stops that have a report
 */
public record StartedRoutes(List<RouteId> started, List<StopId> reported) {

    /** Copies the lists. */
    public StartedRoutes {
        started = List.copyOf(started);
        reported = List.copyOf(reported);
    }

    /**
     * Nothing has happened on the plan's routes.
     *
     * @return the value
     */
    public static StartedRoutes none() {
        return new StartedRoutes(List.of(), List.of());
    }
}
