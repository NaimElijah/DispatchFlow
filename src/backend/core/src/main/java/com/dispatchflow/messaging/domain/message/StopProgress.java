package com.dispatchflow.messaging.domain.message;

/**
 * How far a stop has come, as Live Operations reports it when a trigger runs (FR-373). Messaging
 * keeps its own copy of the concept (DL §2.3, rule 1).
 */
public enum StopProgress {
    /** Neither arrived at nor given an outcome. */
    NOT_REACHED,
    /** Arrived at, with no outcome yet. */
    ARRIVED,
    /** Delivered or failed. */
    HAS_OUTCOME;

    /**
     * Whether the stop still gets the messages of FR-351 to FR-354 and FR-372 (FR-373).
     *
     * @return {@code true} only before the stop is arrived at or has an outcome
     */
    public boolean awaitsDelivery() {
        return this == NOT_REACHED;
    }
}
