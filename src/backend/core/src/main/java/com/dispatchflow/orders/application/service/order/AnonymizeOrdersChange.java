package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link AnonymizeOrders}: one lists the orders due, and each order is
 * anonymized in one of its own, with its audit record, so one stale save doesn't hold back the rest
 * (ADR-129 decision 3).
 *
 * <p>To Be Implemented. It comes with the whole nightly retention run (ADR-121).
 */
@Transactional
public class AnonymizeOrdersChange {

    private final OrderRepository orders;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job's transactions.
     *
     * @param orders where orders are kept
     * @param clock today in {@code Asia/Jerusalem}
     * @param auditLog the audit trail
     */
    public AnonymizeOrdersChange(OrderRepository orders, Clock clock, AuditLog auditLog) {
        this.orders = orders;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The orders dated at least 90 days before today and not yet anonymized. {@link #apply} decides
     * each one.
     *
     * <p>To Be Implemented.
     *
     * @return the orders to anonymize, each once
     */
    @Transactional(readOnly = true)
    public List<OrderId> due() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Removes one order's personal fields through {@code Order.anonymize}, and writes its audit
     * record.
     *
     * <p>To Be Implemented.
     *
     * @param order the order
     * @param call the job's server step
     * @throws RuleViolation with {@code RetentionNotOver} before the 90th day after its date, or
     *     {@code OrderAnonymized} when it is already done
     * @throws StaleVersion when the order changed between its read and its save; the next night
     *     retries it
     */
    public void apply(OrderId order, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
