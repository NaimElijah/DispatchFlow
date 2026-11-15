package com.dispatchflow.reporting.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A stop's effective outcome, as Live Operations' view gives it: the latest dispatcher's correction
 * if there is one, otherwise the first outcome (FR-333, BR-18). A held report is not an outcome
 * until it is confirmed (BR-132).
 */
public sealed interface StopOutcome {

    /** No outcome stands: the stop is pending, only arrived, or not reported (BR-129). */
    record NotReported() implements StopOutcome {}

    /**
     * The stop failed. It counts in neither part of the on-time rate (FR-264), and the summary shows
     * it beside the rate (FR-339).
     */
    record Failed() implements StopOutcome {}

    /**
     * The stop was delivered.
     *
     * @param at when, as corrected for the device's clock (BR-114); for a correction to delivered,
     *     the time the correction states, or the original report's time when it states none (FR-333)
     */
    record Delivered(Instant at) implements StopOutcome {

        /** Checks the time is present. */
        public Delivered {
            Objects.requireNonNull(at, "at");
        }
    }
}
