package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's customer step (BR-98, NFR-257, ADR-075, ADR-102, DL §12): {@code
 * Customer.anonymize}, given the customer's last delivery date, removes its personal data in
 * place a year after its retirement, or, never retired, a year after the later of its creation and
 * its last order, one customer per transaction. A customer whose retention a new order extended
 * refuses with {@code RetentionNotOver}, and the job moves on.
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeCustomers {

    private final CustomerRepository customers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param customers where customers are kept, with each one's last delivery date
     * @param clock the time of the job
     * @param auditLog the audit trail
     */
    public AnonymizeCustomers(CustomerRepository customers, Clock clock, AuditLog auditLog) {
        this.customers = customers;
        this.clock = clock;
        this.auditLog = auditLog;
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
