package com.dispatchflow.messaging.domain;

/**
 * A stop's first outcome, as Live Operations' {@code OutcomeRecorded} states it (FR-355, FR-356,
 * FR-374). Messaging keeps its own copy of the concept (DL §2.3, rule 1).
 */
public sealed interface ReportedOutcome {

    /**
     * Delivered.
     *
     * @param leftAtDoor whether it was left at the door (FR-366)
     */
    record Delivered(boolean leftAtDoor) implements ReportedOutcome {}

    /** Failed; the reason is never sent (FR-355). */
    record Failed() implements ReportedOutcome {}
}
