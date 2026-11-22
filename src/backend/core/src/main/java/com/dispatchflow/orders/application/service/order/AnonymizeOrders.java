package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's order step (NFR-257, ADR-075, DL §12): {@code Order.anonymize}
 * removes the personal fields of every order dated 90 days or more before today, one order per
 * transaction; the order and its figures stay (NFR-152). The scheduler runs it at 03:00 {@code
 * Asia/Jerusalem} (TS §6.3).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeOrders {

    private final OrderRepository orders;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param orders where orders are kept
     * @param clock today in {@code Asia/Jerusalem}
     * @param auditLog the audit trail
     */
    public AnonymizeOrders(OrderRepository orders, Clock clock, AuditLog auditLog) {
        this.orders = orders;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Anonymizes every order due.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many orders were anonymized
     */
    public int anonymizeDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
