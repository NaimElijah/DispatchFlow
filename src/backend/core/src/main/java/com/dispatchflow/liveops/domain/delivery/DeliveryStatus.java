package com.dispatchflow.liveops.domain.delivery;

/**
 * Where a stop's delivery stands, derived from its records (DL §9.3, BR-87): the effective outcome
 * first, then an open held report, then an arrival.
 */
public enum DeliveryStatus {
    /** No fact stands: nothing is recorded, or every held report was refused. */
    PENDING,
    /** The driver arrived, with no outcome yet. */
    ARRIVED,
    /** A report waits for a dispatcher's decision, and no outcome stands (BR-132). */
    HELD,
    /** The effective outcome is delivered. */
    DELIVERED,
    /** The effective outcome is failed. */
    FAILED
}
