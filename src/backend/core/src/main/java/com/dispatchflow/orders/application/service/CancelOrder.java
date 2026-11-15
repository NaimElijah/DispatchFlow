package com.dispatchflow.orders.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.application.spi.StopProgress;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher cancels an order (FR-58): {@code Order.cancel}, after Live Operations' {@link
 * StopProgress} says nothing happened at its stop (BR-92). The order raises {@code
 * OrderCancelled}, which leaves with the save, so Planning takes its stop off the published route
 * at once, with no optimization (FR-207, DL §6.2). The order is never deleted (BR-43).
 *
 * <p>Built in M9.
 */
public final class CancelOrder {

    private final OrderRepository orders;
    private final StopProgress stopProgress;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param orders where orders are kept
     * @param stopProgress what happened at the order's stop, as Live Operations answers
     * @param clock the time of the cancellation
     * @param auditLog the audit trail
     */
    public CancelOrder(OrderRepository orders, StopProgress stopProgress, Clock clock, AuditLog auditLog) {
        this.orders = orders;
        this.stopProgress = stopProgress;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Cancels the order.
     *
     * <p>Built in M9.
     *
     * @param actor the dispatcher
     * @param order the order
     * @param expected the version the dispatcher cancelled (BR-76)
     * @throws NotFound when no order has the ID
     * @throws RuleViolation with {@code OrderAnonymized}, {@code OrderAlreadyCancelled} or {@code
     *     StopAlreadyHasFact} (BR-92), in that order
     * @throws StaleVersion when the order changed since {@code expected}
     */
    public void cancel(Actor actor, OrderId order, Version expected) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
