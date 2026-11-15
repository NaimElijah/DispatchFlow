package com.dispatchflow.shared.application;

import com.dispatchflow.shared.EventId;

/**
 * The listeners' idempotency record: the {@code (listener, event ID)} pairs already handled (DL
 * §7.3, ADR-064). Delivery is at least once, so a listener whose reaction is not idempotent by
 * nature records the pair in the transaction of its reaction, so a reaction that fails forgets
 * the pair and the next delivery runs it again, and it does nothing on a repeat. A phone's
 * idempotent request keys are Live Operations' own record, not this one (placement P2).
 *
 * <p>Built in M0.
 */
public interface ProcessedEvents {

    /**
     * Records that a listener handles an event, and answers whether this is the first delivery. It
     * writes in the caller's transaction, so a rollback forgets the pair and the next delivery runs
     * the reaction again.
     *
     * <p>Built in M0.
     *
     * @param listener the listener's stable name, the same on every delivery
     * @param event the delivered event's ID
     * @return {@code true} the first time the pair is recorded, {@code false} on a repeat
     */
    boolean firstDelivery(String listener, EventId event);
}
