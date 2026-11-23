package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * What a test sees of a {@link Customer}, read through its getters: a value that compares by every
 * part, which the entity doesn't. Tests compare a customer before and after a command, or with the
 * customer they expect, through it.
 *
 * @param id the customer
 * @param name its name, until anonymized
 * @param phone its contact phone, until anonymized
 * @param address its address as typed, until anonymized
 * @param note its standing notes for drivers, if any
 * @param location its location
 * @param flags its address flags, in their declared order
 * @param defaults its default window and service time
 * @param state active, retired or anonymized
 * @param createdAt when it was registered
 */
public record CustomerDescription(
        CustomerId id,
        Optional<ContactName> name,
        Optional<PhoneNumber> phone,
        Optional<Address> address,
        Optional<DriverNote> note,
        Location location,
        Set<AddressFlag> flags,
        OrderDefaults defaults,
        Customer.State state,
        Instant createdAt) {

    /**
     * Describes a customer.
     *
     * @param customer the customer
     * @return what its getters give
     */
    public static CustomerDescription of(Customer customer) {
        return new CustomerDescription(
                customer.id(),
                customer.name(),
                customer.phone(),
                customer.address(),
                customer.note(),
                customer.location(),
                customer.flags(),
                customer.orderDefaults(),
                customer.state(),
                customer.createdAt());
    }
}
