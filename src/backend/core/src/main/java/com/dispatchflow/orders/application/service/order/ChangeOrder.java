package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.application.spi.OrderPlacement;
import com.dispatchflow.orders.application.spi.StopProgress;
import com.dispatchflow.orders.domain.OrderChange;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher changes an order's fields other than its date and reference (FR-57): the use case
 * asks Live Operations, through the {@link StopProgress} port it implements, what happened at the
 * current attempt's stop, translates the answer into the domain's {@code StopState}, and calls
 * {@code Order.change}. No change is made once the stop is arrived at or has an outcome (BR-92).
 * On a published plan the change reaches the route through a re-optimization (FR-206), and the
 * new last change time marks the stop changed since publishing (FR-397).
 *
 * <p>The answer says at once whether the order is on a published route, so the dispatcher knows
 * the driver sees the change only after a re-optimization (FR-396). The use case learns it from
 * Planning, through the {@link OrderPlacement#placementOf} port Planning implements, asked with the
 * order's date and current attempt.
 *
 * <p>Both answers are built in M2 (ADR-121): {@code placementOf} in full, and {@code StopProgress}
 * answering no report for every stop until M5 builds it in full, so until then no change is refused
 * as {@code StopAlreadyHasFact}.
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
@Transactional
public class ChangeOrder {

    private final OrderRepository orders;
    private final StopProgress stopProgress;
    private final OrderPlacement orderPlacement;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param orders where orders are kept
     * @param stopProgress what happened at the order's stop, as Live Operations answers
     * @param orderPlacement where the plans hold the order, as Planning answers (FR-396)
     * @param clock the time of the change
     * @param auditLog the audit trail
     */
    public ChangeOrder(
            OrderRepository orders,
            StopProgress stopProgress,
            OrderPlacement orderPlacement,
            Clock clock,
            AuditLog auditLog) {
        this.orders = orders;
        this.stopProgress = stopProgress;
        this.orderPlacement = orderPlacement;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the order.
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param order the order
     * @param change the new values
     * @param expected the version the dispatcher changed (BR-76)
     * @return whether the changed order is on a published route (FR-396)
     * @throws NotFound when no order has the ID
     * @throws RuleViolation with {@code OrderAnonymized}, {@code OrderAlreadyCancelled}, {@code
     *     StopAlreadyHasFact} (BR-92) or {@code WindowNotOnDate} (BR-35), in that order
     * @throws StaleVersion when the order changed since {@code expected}
     */
    public ChangeOutcome change(Actor actor, OrderId order, OrderChange change, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * What the dispatcher is told after a change (FR-396). It holds no personal data.
     *
     * <p>Built in M2.
     *
     * @param onPublishedRoute whether a route of the order's published plan serves its current
     *     attempt, so the driver sees the change only after a re-optimization (FR-206, FR-396)
     */
    public record ChangeOutcome(boolean onPublishedRoute) {}
}
