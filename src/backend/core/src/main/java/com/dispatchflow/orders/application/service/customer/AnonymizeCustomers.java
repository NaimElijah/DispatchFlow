package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.audit.application.api.CallContext;

/**
 * The nightly retention job's customer step (BR-98, NFR-257, ADR-075, ADR-102, DL §12): {@code
 * Customer.anonymize}, given the customer's last delivery date, removes its personal data in
 * place a year after its retirement, or, never retired, a year after the later of its creation and
 * its last order, one customer per transaction. A customer whose retention a new order extended
 * refuses with {@code RetentionNotOver}, and the job moves on.
 *
 * <p>It lists the customers due, then anonymizes them one by one, and opens no transaction of its
 * own: {@link AnonymizeCustomersChange} holds each customer's (ADR-129).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeCustomers {

    private final AnonymizeCustomersChange change;

    /**
     * Creates the job.
     *
     * @param change the transaction that lists the customers due, and each customer's own
     */
    public AnonymizeCustomers(AnonymizeCustomersChange change) {
        this.change = change;
    }

    /**
     * Anonymizes every customer due.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many customers were anonymized
     */
    public int anonymizeDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
