package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.planning.domain.PlanningError.InvalidTravelLeg;
import com.dispatchflow.shared.RuleViolation;
import java.time.Duration;
import java.util.Objects;

/**
 * One leg of a route as the optimizer timed it: buffered travel seconds and road metres (DL §10.2,
 * FR-401). The plan stores the legs, so live arrivals propagate along them without re-applying a
 * factor (ADR-067).
 *
 * @param travel the buffered travel time
 * @param metres the road distance
 */
public record TravelLeg(Duration travel, long metres) {

    /**
     * Checks the leg.
     *
     * @throws RuleViolation with {@link InvalidTravelLeg} when either part is negative
     */
    public TravelLeg {
        Objects.requireNonNull(travel, "travel");
        if (travel.isNegative() || metres < 0) {
            throw new RuleViolation(new InvalidTravelLeg());
        }
    }

    /**
     * Creates the leg.
     *
     * @param travel the buffered travel time
     * @param metres the road distance
     * @return the leg
     * @throws RuleViolation with {@link InvalidTravelLeg} when either part is negative
     */
    public static TravelLeg of(Duration travel, long metres) {
        return new TravelLeg(travel, metres);
    }
}
