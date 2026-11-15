package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.orders.domain.AddressFlag;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.Customer;
import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.orders.domain.Location;
import com.dispatchflow.orders.domain.OrderDefaults;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Version;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One customer as the dispatcher sees and changes it (FR-08, FR-56, FR-59, FR-63, FR-259): its
 * contact, address, location and flags, its defaults, its state, and its orders from today on, so
 * an order is found by its customer. The version is the one a change sends back (BR-76). It holds
 * personal data, so it prints redacted.
 *
 * <p>Built in M2.
 *
 * @param customer the customer
 * @param name its name, or empty once anonymized (BR-98)
 * @param phone its contact phone, or empty once anonymized
 * @param address its address as typed, or empty once anonymized
 * @param note its standing notes for drivers, if any (FR-08)
 * @param location its location: missing, geocoded or placed by hand (FR-12, FR-14)
 * @param flags its address flags, in their declared order (FR-63)
 * @param defaults its default window and service time (BR-49, BR-124)
 * @param state active, retired or anonymized
 * @param orders its orders dated today or later, cancelled ones included, by date (FR-259)
 * @param version the version the customer was read at, for {@code If-Match} (BR-76)
 */
public record CustomerView(
        CustomerId customer,
        Optional<ContactName> name,
        Optional<PhoneNumber> phone,
        Optional<Address> address,
        Optional<DriverNote> note,
        Location location,
        Set<AddressFlag> flags,
        OrderDefaults defaults,
        Customer.State state,
        List<OrderRow> orders,
        Version version)
        implements PersonalData {

    /** Checks every part is present, possibly empty, and copies the flags and the orders. */
    public CustomerView {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(version, "version");
        // EnumSet.copyOf refuses an empty collection that is not itself an EnumSet.
        flags = flags.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(AddressFlag.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(flags));
        orders = List.copyOf(orders);
    }

    /**
     * A form that hides the customer's data.
     *
     * @return {@code CustomerView[redacted]}
     */
    @Override
    public String toString() {
        return "CustomerView[redacted]";
    }
}
