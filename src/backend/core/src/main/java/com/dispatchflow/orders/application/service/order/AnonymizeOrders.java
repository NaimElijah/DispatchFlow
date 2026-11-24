package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.audit.application.api.CallContext;

/**
 * The nightly retention job's order step (NFR-257, ADR-075, DL §12): {@code Order.anonymize}
 * removes the personal fields of every order dated 90 days or more before today, one order per
 * transaction; the order and its figures stay (NFR-152). The scheduler runs it at 03:00 {@code
 * Asia/Jerusalem} (TS §6.3).
 *
 * <p>It lists the orders due, then anonymizes them one by one, and opens no transaction of its own:
 * {@link AnonymizeOrdersChange} holds each order's (ADR-129).
 *
 * <p>To Be Implemented. It comes with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeOrders {

    private final AnonymizeOrdersChange change;

    /**
     * Creates the job.
     *
     * @param change the transaction that lists the orders due, and each order's own
     */
    public AnonymizeOrders(AnonymizeOrdersChange change) {
        this.change = change;
    }

    /**
     * Anonymizes every order due.
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many orders were anonymized
     */
    public int anonymizeDue(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
