package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.DaySeconds;
import java.util.Objects;

/**
 * A route's planned departure and return, and the leg from its last stop to its end (FR-17, FR-143).
 *
 * @param leavesAt when the route leaves its start
 * @param returnsAt when it reaches its end
 * @param legToEnd the leg into its end
 */
public record RoutePlan(DaySeconds leavesAt, DaySeconds returnsAt, TravelLeg legToEnd) {

    /** Checks every part is present. */
    public RoutePlan {
        Objects.requireNonNull(leavesAt, "leavesAt");
        Objects.requireNonNull(returnsAt, "returnsAt");
        Objects.requireNonNull(legToEnd, "legToEnd");
    }
}
