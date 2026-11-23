package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.PlanningError.InvalidTravelBuckets;
import com.dispatchflow.shared.RuleViolation;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A day's three travel-time buckets: the morning peak, the evening peak, and off-peak for the rest
 * of the day (FR-321). The hours and factors are estimates, calibrated from pilot arrivals.
 *
 * <p>The settings embed them in seven of their columns. Their constructor checks a rule, so
 * Planning's persistence adapter registers an instantiator that rebuilds them through {@code
 * StoredStateInvalid.whileRestoring}, and stored peaks that overlap are an incident (ADR-126).
 *
 * @param morningPeak the morning peak
 * @param eveningPeak the evening peak, which starts no earlier than the morning peak ends
 * @param offPeak the factor for the rest of the day
 */
@Embeddable
public record TravelBuckets(
        @Embedded TravelBucket morningPeak, @Embedded TravelBucket eveningPeak, TravelFactor offPeak) {

    /** The buckets FR-321 starts with: 07:00–09:30 and 15:30–18:30 at 1.3, and 1.0 otherwise. */
    public static final TravelBuckets STARTING = new TravelBuckets(
            new TravelBucket(LocalTime.of(7, 0), LocalTime.of(9, 30), TravelFactor.of(new BigDecimal("1.3"))),
            new TravelBucket(LocalTime.of(15, 30), LocalTime.of(18, 30), TravelFactor.of(new BigDecimal("1.3"))),
            TravelFactor.of(BigDecimal.ONE));

    /**
     * Checks the peaks don't overlap.
     *
     * @throws RuleViolation with {@link InvalidTravelBuckets} when the evening peak starts before the
     *     morning peak ends
     */
    public TravelBuckets {
        Objects.requireNonNull(morningPeak, "morningPeak");
        Objects.requireNonNull(eveningPeak, "eveningPeak");
        Objects.requireNonNull(offPeak, "offPeak");
        if (eveningPeak.start().isBefore(morningPeak.end())) {
            throw new RuleViolation(new InvalidTravelBuckets());
        }
    }
}
