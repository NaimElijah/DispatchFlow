package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What a stop's {@link Delivery} shows for a projection (DL §10.4): when the driver arrived, and when
 * its outcome happened, each by the corrected time.
 *
 * @param stop the stop
 * @param arrivedAt the arrival's time, if any
 * @param outcomeAt the effective outcome's time, if any
 */
public record StopFact(StopId stop, Optional<Instant> arrivedAt, Optional<Instant> outcomeAt) {

    /** Checks every part is present. */
    public StopFact {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(arrivedAt, "arrivedAt");
        Objects.requireNonNull(outcomeAt, "outcomeAt");
    }

    /**
     * The fact a delivery shows.
     *
     * @param delivery the stop's delivery
     * @return its arrival and outcome times
     */
    public static StopFact of(Delivery delivery) {
        return new StopFact(delivery.stop(), delivery.arrivedAt(), delivery.outcomeTime());
    }

    /**
     * Whether anything is recorded for the stop.
     *
     * @return {@code true} with an arrival or an outcome
     */
    public boolean isReported() {
        return arrivedAt.isPresent() || outcomeAt.isPresent();
    }

    /**
     * The time of the stop's latest event (FR-383).
     *
     * @return the later of the arrival and the outcome, or empty when neither is recorded
     */
    public Optional<Instant> latest() {
        if (arrivedAt.isPresent() && outcomeAt.isPresent()) {
            return Optional.of(arrivedAt.get().isAfter(outcomeAt.get()) ? arrivedAt.get() : outcomeAt.get());
        }
        return arrivedAt.or(() -> outcomeAt);
    }
}
