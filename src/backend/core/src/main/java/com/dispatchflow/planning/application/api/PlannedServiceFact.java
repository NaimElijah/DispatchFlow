package com.dispatchflow.planning.application.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Whether a plan serves an order, and when its service is planned to start (FR-266, BR-04).
 * Reporting translates it into its {@code PlannedService}.
 *
 * <p>Built in M5.
 */
public sealed interface PlannedServiceFact {

    /**
     * The order is served.
     *
     * <p>Built in M5.
     *
     * @param start when its service is planned to start
     */
    record Served(Instant start) implements PlannedServiceFact {

        /** Checks the start is present. */
        public Served {
            Objects.requireNonNull(start, "start");
        }
    }

    /**
     * The order is dropped, so it counts as not on time (FR-266).
     *
     * <p>Built in M5.
     */
    record Dropped() implements PlannedServiceFact {}
}
