package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.DaySeconds;
import java.util.Objects;

/** Whether a plan serves an order, and when its service is planned to start (FR-266, BR-04). */
public sealed interface PlannedService {

    /**
     * The order is on a route.
     *
     * @param start when its service is planned to start, as the validator timed it (ADR-099)
     */
    record Served(DaySeconds start) implements PlannedService {

        /** Checks the start is present. */
        public Served {
            Objects.requireNonNull(start, "start");
        }
    }

    /** The order is dropped, so it counts as not on time (FR-266). */
    record Dropped() implements PlannedService {}
}
