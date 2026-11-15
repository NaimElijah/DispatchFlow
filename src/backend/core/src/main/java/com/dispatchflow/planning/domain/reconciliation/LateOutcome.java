package com.dispatchflow.planning.domain.reconciliation;

/** How a stop came onto the route that served it after its fact arrived late (DL §8, DL §17). */
public enum LateOutcome {
    /** The plan had moved or dropped the stop before its fact arrived (BR-89). */
    SERVED_LATE,
    /** Its order had been cancelled after the fact's corrected time (BR-130). */
    SERVED_AFTER_CANCELLATION
}
