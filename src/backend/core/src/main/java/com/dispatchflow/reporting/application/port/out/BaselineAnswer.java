package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.PlannedFigures;
import java.util.Objects;

/**
 * The optimizer's answer to a baseline solve (FR-39, FR-342): the baseline's figures, or the
 * optimizer's refusal when its solver failed, found no solution within its budget, or its validator
 * rejected the solution (ADR-004, BR-15). An optimizer that can't be reached is no answer: the call
 * fails with an unchecked exception instead (see {@link BaselinePlanner}).
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
     * The optimizer answered, and found no baseline. Asking again with the same request gets the
     * same answer, so the listener completes instead of failing its delivery.
     *
     * <p>Built in M5.
     *
     * @param reasonCode the optimizer's stable code for why, as {@code optimizer-api} v1 names it
     */
    record Refused(String reasonCode) implements BaselineAnswer {

        /** Checks the code is present. */
        public Refused {
            Objects.requireNonNull(reasonCode, "reasonCode");
        }
    }
}
