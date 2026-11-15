package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.InvalidRouteTimes;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * A route's planned departure and return, and the leg from its last stop to its end (FR-17, FR-143).
 * A route the validator couldn't time to its end, because it has no stops or no road to its end, has
 * none (ADR-112, ADR-113). The return may pass the date's end on a route that breaks a time rule.
 *
 * @param leavesAt when the route leaves its start
 * @param returnsAt when it reaches its end
 * @param legToEnd the leg into its end
 */
public record RoutePlan(SolveTime leavesAt, SolveTime returnsAt, TravelLeg legToEnd) {

    /**
     * Checks the times, as {@link StopTimes} checks a stop's (ADR-101, ADR-112).
     *
     * @throws RuleViolation with {@link InvalidRouteTimes} when the return is before the departure,
     *     or the two count from different dates
     */
    public RoutePlan {
        Objects.requireNonNull(leavesAt, "leavesAt");
        Objects.requireNonNull(returnsAt, "returnsAt");
        Objects.requireNonNull(legToEnd, "legToEnd");
        if (!leavesAt.date().equals(returnsAt.date()) || returnsAt.seconds() < leavesAt.seconds()) {
            throw new RuleViolation(new InvalidRouteTimes());
        }
    }
}
