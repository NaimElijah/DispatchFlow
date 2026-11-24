package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.application.spi.StopProgress;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher moves an order to another date (FR-57, FR-315): {@code Order.reschedule} on the new
 * date's capacity, read under its lock, the new date being the one the new window carries, after
 * Live Operations' {@link StopProgress} says nothing happened at its stop (BR-92). The order raises
 * {@code OrderRescheduled}, which leaves with the save, so Planning takes its stop off the old
 * date's plan at once (FR-315, DL §6.2).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class RescheduleOrder {

    private final OrderRepository orders;
    private final StopProgress stopProgress;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param orders where orders are kept, with each date's capacity
     * @param stopProgress what happened at the order's stop, as Live Operations answers
     * @param clock today and the time of the move
     * @param auditLog the audit trail
     */
    public RescheduleOrder(OrderRepository orders, StopProgress stopProgress, Clock clock, AuditLog auditLog) {
        this.orders = orders;
        this.stopProgress = stopProgress;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Moves the order.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param order the order
     * @param window the window on the new date, which it carries
     * @param expected the version the dispatcher moved (BR-76)
     * @throws NotFound when no order has the ID
     * @throws RuleViolation with the first of, in this order: {@code OrderAnonymized}, {@code
     *     OrderAlreadyCancelled}, {@code StopAlreadyHasFact} (BR-92), {@code AlreadyOnThatDate},
     *     {@code DateInPast} (BR-116), {@code WindowNotOnDate} (BR-35), {@code
     *     DuplicateOrderReference} (FR-211), {@code DateFull} (BR-101)
     * @throws StaleVersion when the order changed since {@code expected}
     */
    public void reschedule(Actor actor, OrderId order, TimeWindow window, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
