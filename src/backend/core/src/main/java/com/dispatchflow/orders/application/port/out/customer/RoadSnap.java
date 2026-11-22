package com.dispatchflow.orders.application.port.out.customer;

import com.dispatchflow.orders.application.port.out.Geocoder;
import com.dispatchflow.shared.GeoPoint;

/**
 * The road network's distance from a geocoded point to the nearest road, which flags a point more
 * than {@code Customer.FAR_FROM_ROAD_METRES} from one (FR-63). OSRM reports it as the snap
 * distance, {@code waypoints[].distance} (ADR-085). OSRM runs on our own host, so the point leaves
 * for no outside service.
 *
 * <p>The geocoding job calls it before its transaction opens (DL §6.3), as it calls the {@link
 * Geocoder}.
 *
 * <p>Built in M7.
 */
public interface RoadSnap {

    /**
     * Measures how far a point lies from the nearest road.
     *
     * <p>Built in M7.
     *
     * @param point the point the geocoder found
     * @return the distance in metres, 0 or more
     * @throws RoadSnapUnavailable when the road network can't be reached or refuses the call, so
     *     the request stays in the outbox and is tried again (FR-64)
     */
    double metresToRoad(GeoPoint point);
}
