package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.access.application.api.CompanyNotSetUp;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.GeocodeRequests;
import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.orders.domain.OrderDefaults;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import java.util.Objects;
import java.util.Optional;

/**
 * A dispatcher creates a customer (FR-08, FR-59): {@code Customer.register} with a new ID, its
 * defaults from the form or, when the form gives none, the company's (FR-125, ADR-104). The phone
 * arrives already checked and in E.164 form, because the web adapter builds the {@link
 * PhoneNumber} at the boundary (FR-377). The customer has no location until geocoding answers, so
 * the use case writes a geocoding request in the same transaction from M7 on (FR-12, DL §6.1).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class RegisterCustomer {

    private final CustomerRepository customers;
    private final GeocodeRequests geocodeRequests;
    private final Company company;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param customers where customers are kept
     * @param geocodeRequests the geocoding outbox
     * @param company Access's company profile, for the defaults a form leaves out
     * @param ids the source of the new customer's ID
     * @param clock the time of creation
     * @param auditLog the audit trail
     */
    public RegisterCustomer(
            CustomerRepository customers,
            GeocodeRequests geocodeRequests,
            Company company,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.customers = customers;
        this.geocodeRequests = geocodeRequests;
        this.company = company;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Registers the customer.
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param command what the form gives
     * @return the new customer's ID
     * @throws CompanyNotSetUp when the form gives no defaults and no Admin has set the company
     *     profile up yet
     */
    public CustomerId register(Actor actor, Command command) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * What the customer form gives (FR-08, FR-59). It holds personal data, so it prints redacted.
     *
     * <p>Built in M2.
     *
     * @param name the customer's name
     * @param phone its contact phone, in E.164 form (FR-377)
     * @param address its address as typed
     * @param note its standing notes for drivers, if any (FR-08)
     * @param defaults its default window and service time, or empty for the company's (FR-125)
     */
    public record Command(
            ContactName name,
            PhoneNumber phone,
            Address address,
            Optional<DriverNote> note,
            Optional<OrderDefaults> defaults)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public Command {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(phone, "phone");
            Objects.requireNonNull(address, "address");
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
