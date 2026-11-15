package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.PlanningError.InvalidTravelBuckets;
import com.dispatchflow.shared.RuleViolation;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A part of the day with its own travel-time factor, its hours read from local wall-clock time and
 * including its start but not its end (FR-321, FR-408, DL §17 "Travel bucket").
 *
 * @param start the first moment of the bucket
 * @param end the moment it ends, not included
 * @param factor its factor
 */
public record TravelBucket(LocalTime start, LocalTime end, TravelFactor factor) {

    /**
     * Checks the hours.
     *
     * @throws RuleViolation with {@link InvalidTravelBuckets} when the start is not before the end
     */
    public TravelBucket {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(factor, "factor");
        if (!start.isBefore(end)) {
            throw new RuleViolation(new InvalidTravelBuckets());
        }
    }
}
