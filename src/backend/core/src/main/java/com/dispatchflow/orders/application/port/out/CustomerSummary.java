package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.Customer;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import java.util.Objects;

/**
 * One customer in a list a dispatcher searches or is warned with (FR-134, FR-212, FR-259). An
 * anonymized customer has no name, phone or address to show, so it is never in such a list. The
 * name, phone and address are personal data, so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param customer the customer
 * @param name its name (FR-59)
 * @param phone its contact phone (FR-59)
 * @param address its address as typed
 * @param state active or retired (FR-56)
 */
public record CustomerSummary(
        CustomerId customer, ContactName name, PhoneNumber phone, Address address, Customer.State state)
        implements PersonalData {

    /** Checks every part is present. */
    public CustomerSummary {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(state, "state");
    }

    /**
     * A form that hides the customer's contact and address.
     *
     * @return {@code CustomerSummary[redacted]}
     */
    @Override
    public String toString() {
        return "CustomerSummary[redacted]";
    }
}
