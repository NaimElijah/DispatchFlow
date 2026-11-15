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
     * The base travel times and road distances between every pair of distinct points (NFR-57). A
     * daily solve asks it when its plan has no stored matrix over its locations. A re-optimization,
     * an edit check or the publishing re-check asks it, over the whole new list of locations, when
     * the plan's stored matrix lacks one of them, such as a new or late order's address (FR-35,
     * ADR-121).
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
     * location of its matrix, depots included (ADR-050, ADR-052). Its matrix is the plan's stored
     * one when that has every location, and otherwise the one {@link #between} answered over the new
     * list (ADR-121). Whether a position is usable is the optimizer's to judge, so every latest
     * position gets its row (BR-80, ADR-050).
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
