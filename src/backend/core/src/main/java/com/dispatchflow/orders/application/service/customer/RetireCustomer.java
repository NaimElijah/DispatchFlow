package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.application.spi.RecordUsage;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher retires a customer, which is never deleted (FR-56, BR-43): the use case reads every
 * order of the customer not yet anonymized, with no date filter, asks Planning, through the {@link
 * RecordUsage} port it implements, which plans not yet completed use them, translates the answer
 * into the domain's {@code InUse}, and calls {@code Customer.retire}. A retired customer is no
 * longer matched by imports.
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class RetireCustomer {

    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final RecordUsage recordUsage;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param customers where customers are kept
     * @param orders where the customer's orders are read
     * @param recordUsage which plans use the customer, as Planning answers
     * @param clock the time of retiring
     * @param auditLog the audit trail
     */
    public RetireCustomer(
            CustomerRepository customers,
            OrderRepository orders,
            RecordUsage recordUsage,
            Clock clock,
            AuditLog auditLog) {
        this.customers = customers;
        this.orders = orders;
        this.recordUsage = recordUsage;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Retires the customer.
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param customer the customer
     * @param expected the version the dispatcher retired (BR-76)
     * @throws NotFound when no customer has the ID
     * @throws RuleViolation with {@code CustomerAnonymized}, {@code CustomerRetired} or {@code
     *     CustomerInUse} (BR-43), in that order
     * @throws StaleVersion when the customer changed since {@code expected}
     */
    public void retire(Actor actor, CustomerId customer, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
