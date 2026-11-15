package com.dispatchflow.liveops.domain.alert;

/**
 * What a refused report stated, which keys its alert (BR-90, BR-135, FR-157, ADR-114). An arrival
 * timed at or after a cancellation is refused too, so it is one of them beside the two outcomes.
 */
public enum RefusedFact {
    /** The driver reported arriving at the stop (FR-29). */
    ARRIVED,
    /** The driver reported the stop delivered (FR-29). */
    DELIVERED,
    /** The driver reported the stop failed (FR-29, FR-242). */
    FAILED
}
