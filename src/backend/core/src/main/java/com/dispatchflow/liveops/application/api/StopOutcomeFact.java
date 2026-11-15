package com.dispatchflow.liveops.application.api;

import java.time.Instant;
import java.util.Objects;

/**
 * A stop's effective outcome: the latest dispatcher's correction if there is one, otherwise the
 * first outcome (FR-333, BR-18). A held report is not an outcome until it is confirmed (BR-132).
 * Reporting translates it into its {@code StopOutcome}.
 *
 * <p>Built in M5.
 */
public sealed interface StopOutcomeFact {

    /**
     * No outcome stands: the stop is pending, only arrived, held or not reported (BR-129).
     *
     * <p>Built in M5.
     */
    record NotReported() implements StopOutcomeFact {}

    /**
     * The effective outcome is failed.
     *
     * <p>Built in M5.
     */
    record Failed() implements StopOutcomeFact {}

    /**
     * The effective outcome is delivered.
     *
     * <p>Built in M5.
     *
     * @param at when it was delivered, as corrected for the device's clock (BR-114, ADR-107)
     */
    record Delivered(Instant at) implements StopOutcomeFact {

        /** Checks the time is present. */
        public Delivered {
            Objects.requireNonNull(at, "at");
        }
    }
}
