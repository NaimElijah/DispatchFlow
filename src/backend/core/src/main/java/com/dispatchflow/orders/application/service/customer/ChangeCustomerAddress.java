package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.GeocodeRequests;
import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher changes a customer's address (BR-104): {@code Customer.changeAddress} clears the
 * location, hand-placed or not, and its flags. When the address changed, the use case writes a
 * geocoding request in the same transaction from M7 on (FR-12, DL §6.1). The screen warns the
 * dispatcher before.
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
@Transactional
public class ChangeCustomerAddress {

    private final CustomerRepository customers;
    private final GeocodeRequests geocodeRequests;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param customers where customers are kept
     * @param geocodeRequests the geocoding outbox
     * @param clock the time of the change
     * @param auditLog the audit trail
     */
    public ChangeCustomerAddress(
            CustomerRepository customers, GeocodeRequests geocodeRequests, Clock clock, AuditLog auditLog) {
        this.customers = customers;
        this.geocodeRequests = geocodeRequests;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the address; the same text changes nothing.
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param customer the customer
     * @param address the address as typed
     * @param expected the version the dispatcher changed (BR-76)
     * @throws NotFound when no customer has the ID
     * @throws RuleViolation with {@code CustomerAnonymized} or {@code CustomerRetired}
     * @throws StaleVersion when the customer changed since {@code expected}
     */
    public void change(Actor actor, CustomerId customer, Address address, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
