package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.orders.domain.AddressFlag;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.Location;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PersonalData;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One active customer whose address carries a flag (FR-63), as the dispatcher checks it before a
 * solve and the map shows it apart from a routed stop (FR-149). A flag never stops the solve
 * (FR-216). The name, address and location are personal data, so it prints redacted.
 *
 * <p>Built in M7.
 *
 * @param customer the customer
 * @param name its name
 * @param address its address as typed
 * @param location its location, missing or geocoded; a hand-placed one carries no flag (FR-217)
 * @param flags its flags, at least one, in their declared order
 */
public record FlaggedCustomer(
        CustomerId customer, ContactName name, Address address, Location location, Set<AddressFlag> flags)
        implements PersonalData {

    /** Checks every part is present, and copies the flags. */
    public FlaggedCustomer {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(location, "location");
        // EnumSet.copyOf refuses an empty collection that is not itself an EnumSet.
        flags = flags.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(AddressFlag.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(flags));
    }

    /**
     * A form that hides the customer's data.
     *
     * @return {@code FlaggedCustomer[redacted]}
     */
    @Override
    public String toString() {
        return "FlaggedCustomer[redacted]";
    }
}
