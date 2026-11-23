package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link AnonymizeCustomers}: one lists the customers due, and each customer is
 * anonymized in one of its own, with its audit record, so one stale save doesn't hold back the rest
 * (ADR-129 decision 3).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
@Transactional
public class AnonymizeCustomersChange {

    private final CustomerRepository customers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job's transactions.
     *
     * @param customers where customers are kept, with each one's last delivery date
     * @param clock the time of the job
     * @param auditLog the audit trail
     */
    public AnonymizeCustomersChange(CustomerRepository customers, Clock clock, AuditLog auditLog) {
        this.customers = customers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The customers whose retention may be over now. {@link #apply} decides each one.
     *
     * <p>Built in M13.
     *
     * @return the customers to try, each once
     */
    @Transactional(readOnly = true)
    public List<CustomerId> due() {
        throw new UnsupportedOperationException("Built in M13");
    }

    /**
     * Anonymizes one customer through {@code Customer.anonymize}, with its last delivery date, and
     * writes its audit record.
     *
     * <p>Built in M13.
     *
     * @param customer the customer
     * @param call the job's server step
     * @throws RuleViolation with {@code RetentionNotOver} when a new order extended the customer's
     *     retention, or {@code CustomerAnonymized} when it is already done
     * @throws StaleVersion when the customer changed between its read and its save; the next night
     *     retries it
     */
    public void apply(CustomerId customer, CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
