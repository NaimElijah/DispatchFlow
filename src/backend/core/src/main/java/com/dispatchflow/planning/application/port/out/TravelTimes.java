package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.shared.GeoPoint;
import java.util.List;

/**
 * The road network's travel times between points, from OSRM's {@code table} service (TS §6.4, DL
 * §10.2, ADR-052). It answers base durations only; the optimizer applies the bucket factors and the
 * buffer (FR-321, FR-322). Building a solve's matrix counts toward the response time but not toward
 * the search limit (BR-75, NFR-07).
 *
 * <p>Built in M3.
 */
public interface TravelTimes {

    /**
     * The base travel times and road distances between every pair of distinct points (NFR-57).
     *
     * <p>Built in M3.
     *
     * @param points the points, each once; two orders at one address share a point (ADR-052)
     * @return the matrix over the points, in the order given, with {@link TravelMatrix#NO_ROUTE} for
     *     a pair the road network can't join (FR-66)
     * @throws java.io.UncheckedIOException when the road network can't be reached, which fails the
     *     solve as <em>road network unavailable</em> (FR-65)
     */
    TravelMatrix between(List<GeoPoint> points);
}
