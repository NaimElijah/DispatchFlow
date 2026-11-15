package com.dispatchflow.planning.application.spi;

import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One reported stop of a date, as a re-optimization takes it as fact (ADR-050 point 1, BR-10,
 * BR-91): its status and its times, each corrected for the device's clock (BR-114). A held report
 * is not a fact until a dispatcher confirms it (BR-132).
 *
 * <p>Built in M9.
 *
 * @param stop the stop
 * @param status what stands at the stop
 * @param arrivedAt when the driver arrived, if an arrival is recorded
 * @param outcomeAt when the effective outcome happened, for a delivered or failed stop
 */
public record LiveStopFact(StopId stop, Status status, Optional<Instant> arrivedAt, Optional<Instant> outcomeAt) {

    /**
     * What stands at a reported stop.
     *
     * <p>Built in M9.
     */
    public enum Status {
        /** The driver arrived, with no outcome yet, so the stop is in progress. */
        ARRIVED,
        /** The effective outcome is delivered. */
        DELIVERED,
        /** The effective outcome is failed. */
        FAILED
    }

    /** Checks every part is present, possibly empty. */
    public LiveStopFact {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(arrivedAt, "arrivedAt");
        Objects.requireNonNull(outcomeAt, "outcomeAt");
    }
}
