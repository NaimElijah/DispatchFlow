package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.shared.GeoPoint;
import java.util.List;

/**
 * The road network's travel times between points, from OSRM's {@code table} service (TS §6.4, DL
 * §10.2, ADR-052). It answers base durations only; the optimizer applies the bucket factors and the
 * buffer (FR-321, FR-322). Building a solve's matrix counts toward the response time but not toward
 * the search limit (BR-75, NFR-07).
 *
 * <p>Built in M3 and M9, method by method.
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
     * @throws RoadNetworkUnavailable when the road network can't be reached, which fails the solve as
     *     <em>road network unavailable</em> (FR-65)
     */
    TravelMatrix between(List<GeoPoint> points);

    /**
     * The base travel times and road distances from each source to each target, in one {@code table}
     * call: a re-optimization adds these rows, one from each vehicle's latest position to every
     * location of the plan's stored matrix, depots included, and asks for no new matrix (ADR-050,
     * ADR-052). Whether a position is usable is the optimizer's to judge, so every latest position
     * gets its row (BR-80, ADR-050).
     *
     * <p>Built in M9.
     *
     * @param sources the points the rows start from, each once
     * @param targets the points the rows reach, each once
     * @return the rows over {@code sources × targets}, in the orders given, with {@link
     *     TravelMatrix#NO_ROUTE} for a pair the road network can't join (FR-66)
     * @throws RoadNetworkUnavailable when the road network can't be reached
     */
    TravelRows from(List<GeoPoint> sources, List<GeoPoint> targets);
}
