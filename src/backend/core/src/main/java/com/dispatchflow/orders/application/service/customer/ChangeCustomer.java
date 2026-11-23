package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.orders.domain.OrderDefaults;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher changes a customer's name, phone, notes for drivers or defaults (FR-56, FR-59). The
 * address changes through {@link ChangeCustomerAddress}, because it clears the location (BR-104).
 *
 * <p>Domain gap: {@code Customer} has no method that changes these fields yet; M2 adds it, refusing
 * a retired or anonymized customer as its other commands do.
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
@Transactional
public class ChangeCustomer {

    private final CustomerRepository customers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param customers where customers are kept
     * @param clock the time of the change, for the audit record
     * @param auditLog the audit trail
     */
    public ChangeCustomer(CustomerRepository customers, Clock clock, AuditLog auditLog) {
        this.customers = customers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the customer.
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param customer the customer
     * @param command the new values
     * @param expected the version the dispatcher changed (BR-76)
     * @throws NotFound when no customer has the ID
     * @throws RuleViolation with {@code CustomerAnonymized} or {@code CustomerRetired}
     * @throws StaleVersion when the customer changed since {@code expected}
     */
    public void change(Actor actor, CustomerId customer, Command command, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * The customer's new contact, notes and defaults (FR-08, FR-59). It holds personal data, so it
     * prints redacted.
     *
     * <p>Built in M2.
     *
     * @param name the name
     * @param phone the contact phone, in E.164 form (FR-377)
     * @param note the standing notes for drivers, if any (FR-08)
     * @param defaults the default window and service time (BR-49, BR-124)
     */
    public record Command(ContactName name, PhoneNumber phone, Optional<DriverNote> note, OrderDefaults defaults)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public Command {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(phone, "phone");
            Objects.requireNonNull(note, "note");
            Objects.requireNonNull(defaults, "defaults");
        }

        /**
         * A form that hides the customer's data.
         *
         * @return {@code Command[redacted]}
         */
        @Override
        public String toString() {
            return "Command[redacted]";
        }
    }
}
