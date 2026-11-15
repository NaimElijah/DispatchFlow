package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.shared.StoredStateInvalid;

/**
 * A route's stable index from 1 to 30, which is its label and its colour slot on the map, the
 * timeline, lists and printouts (FR-148, ADR-101). A re-solve keeps a vehicle's index. The range
 * doesn't follow the 15-vehicle limit, because a withdrawn route keeps its index beside its
 * vehicle's new one.
 *
 * <p>Only {@link Plan} assigns one, so an index outside the range can only be damaged stored state.
 *
 * @param value the index, 1 to 30
 */
public record RouteIndex(int value) implements Comparable<RouteIndex> {

    /** The lowest index. */
    public static final int LOWEST = 1;

    /** The highest index. */
    public static final int HIGHEST = 30;

    /**
     * Checks a stored index.
     *
     * @throws StoredStateInvalid when it is outside 1 to 30
     */
    public RouteIndex {
        if (value < LOWEST || value > HIGHEST) {
            throw new StoredStateInvalid(RouteIndex.class, "a route index is 1 to 30");
        }
    }

    /**
     * Rebuilds a stored index.
     *
     * @param value the stored index
     * @return the index
     * @throws StoredStateInvalid when it is outside 1 to 30
     */
    public static RouteIndex of(int value) {
        return new RouteIndex(value);
    }

    @Override
    public int compareTo(RouteIndex other) {
        return Integer.compare(value, other.value);
    }
}
