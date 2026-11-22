package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.baseline.PlannedFigures;
import java.util.Objects;

/**
 * The optimizer's answer to a baseline solve (FR-39, FR-342): the baseline's figures, or the
 * optimizer's refusal, with its {@link RefusalCause}, when its solver failed, found no solution
 * within its budget, or its validator rejected the solution (ADR-004, BR-15). An optimizer that
 * can't be reached, or answers what the adapter doesn't know, is no answer: the call fails with an
 * unchecked exception instead (see {@link BaselinePlanner}).
 *
 * <p>Built in M5.
 */
public sealed interface BaselineAnswer {

    /**
     * The optimizer solved the baseline and its validator accepted it.
     *
     * <p>Built in M5.
     *
     * @param figures the baseline's distance, driver-hours and each order's planned service or drop
     */
    record Planned(PlannedFigures figures) implements BaselineAnswer {

        /** Checks the figures are present. */
        public Planned {
            Objects.requireNonNull(figures, "figures");
        }
    }

    /**
     * The optimizer answered, and found no baseline. Every baseline request carries the same seed and
     * a solution budget, never a wall-clock limit, so asking again with the same request gets the
     * same answer from a deterministic solver (NFR-13). The listener records the refusal and
     * completes instead of failing its delivery.
     *
     * <p>Built in M5.
     *
     * @param cause why, as the adapter maps the optimizer's answer: its failure's closed kind, or its
     *     validator's rejection
     */
    record Refused(RefusalCause cause) implements BaselineAnswer {

        /** Checks the cause is present. */
        public Refused {
            Objects.requireNonNull(cause, "cause");
        }
    }
}
