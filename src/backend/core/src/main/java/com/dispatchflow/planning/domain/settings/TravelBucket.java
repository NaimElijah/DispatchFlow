package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.PlanningError.InvalidTravelBuckets;
import com.dispatchflow.shared.RuleViolation;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A part of the day with its own travel-time factor, its hours read from local wall-clock time and
 * including its start but not its end (FR-321, FR-408, DL §17 "Travel bucket").
 *
 * <p>The settings embed each peak in three of their columns. Its constructor checks a rule, so
 * Planning's persistence adapter registers an instantiator that rebuilds it through {@code
 * StoredStateInvalid.whileRestoring}, and stored hours the rule refuses are an incident (ADR-126).
 *
 * @param start the first moment of the bucket
 * @param end the moment it ends, not included
 * @param factor its factor
 */
@Embeddable
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
