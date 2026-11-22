package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.planning.application.port.out.RoadNetworkUnavailable;
import com.dispatchflow.shared.GeoPoint;
import java.util.List;

/**
 * The road network's line through a route's points, from OSRM's {@code route} service, so the map
 * draws each route along the roads rather than as straight lines (FR-24, TS §6.4).
 *
 * <p>Built in M3.
 */
public interface RouteShapes {

    /**
     * The road line through the points, in order.
     *
     * <p>Built in M3.
     *
     * @param waypoints the route's start depot, its stops in order and its end depot
     * @return the line along the roads
     * @throws RoadNetworkUnavailable when the road network can't be reached
     */
    RouteShape along(List<GeoPoint> waypoints);
}
