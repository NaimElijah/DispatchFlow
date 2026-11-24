package com.dispatchflow.planning.application.spi;

import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.List;

/**
 * What has happened on a plan's routes, as Live Operations answers; Planning translates it into
 * its {@code StartedRoutes}, which cancelling a plan judges (FR-336).
 *
 * <p>To Be Implemented.
 *
 * @param started the routes whose run has started
 * @param reported the stops, among the stops given, with an accepted outcome or an arrival; a stop
 *     recorded on a driver's behalf starts no route, so both lists count
 */
public record StartedRoutesFact(List<RouteId> started, List<StopId> reported) {

    /** Copies the lists. */
    public StartedRoutesFact {
        started = List.copyOf(started);
        reported = List.copyOf(reported);
    }
}
