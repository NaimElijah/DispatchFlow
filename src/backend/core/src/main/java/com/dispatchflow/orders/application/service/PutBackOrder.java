package com.dispatchflow.orders.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.application.spi.OrderPlacement;
import com.dispatchflow.orders.application.spi.StopProgress;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher puts a failed or dropped order back, for today's re-optimization or on another
 * date, as its next attempt (FR-75, FR-258, BR-95): the use case asks Planning's {@link
 * OrderPlacement} where the current attempt is placed and Live Operations' {@link StopProgress}
 * what happened at its stop, translates both, and calls {@code Order.putBack} on the target date's
 * capacity, read under its lock; the target date is the one the new window carries. On its own date it doesn't count again toward the limit (BR-113);
 * on another date the order raises {@code OrderPutBack}, which leaves with the save, so Planning
 * takes the old attempt's drop off the old date's plan (ADR-115).
 *
 * <p>Built in M9.
 */
public final class PutBackOrder {

    private final OrderRepository orders;
    private final OrderPlacement orderPlacement;
    private final StopProgress stopProgress;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param orders where orders are kept, with each date's capacity
     * @param orderPlacement where the plans hold the order, as Planning answers
     * @param stopProgress what happened at the order's stop, as Live Operations answers
     * @param clock today and the time of the put-back
     * @param auditLog the audit trail
     */
    public PutBackOrder(
            OrderRepository orders,
            OrderPlacement orderPlacement,
            StopProgress stopProgress,
            Clock clock,
            AuditLog auditLog) {
        this.orders = orders;
        this.orderPlacement = orderPlacement;
        this.stopProgress = stopProgress;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Puts the order back.
     *
     * <p>Built in M9.
     *
     * @param actor the dispatcher
     * @param order the order
     * @param window the window on the target date, which it carries: the order's own date, or
     *     another
     * @param expected the version the dispatcher put back (BR-76)
     * @throws NotFound when no order has the ID
     * @throws RuleViolation with the first of, in this order: {@code OrderAnonymized}, {@code
     *     OrderAlreadyCancelled}, {@code OnlyFailedOrDroppedOrdersArePutBack} (BR-95), {@code
     *     DateInPast} (BR-116), {@code WindowNotOnDate} (BR-35), {@code DuplicateOrderReference}
     *     (FR-211), {@code DateFull} (BR-101)
     * @throws StaleVersion when the order changed since {@code expected}
     */
    public void putBack(Actor actor, OrderId order, TimeWindow window, Version expected) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
