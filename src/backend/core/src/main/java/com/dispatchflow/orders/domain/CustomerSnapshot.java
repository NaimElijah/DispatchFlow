package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything a {@link Customer} holds, as the persistence adapter stores and reloads it. {@link
 * Customer#restore} checks the rules that hold at rest. The normalized address is not stored here:
 * the customer computes it from its address.
 *
 * @param id the customer
 * @param name its name; empty once anonymized
 * @param phone its contact phone; empty once anonymized
 * @param address its address as typed; empty once anonymized
 * @param note its standing notes for drivers, if any
 * @param location its location
 * @param flags its address flags, in their declared order
 * @param defaults its default window and service time
 * @param state active, retired or anonymized
 * @param createdAt when it was registered, from which a customer with no order counts its retention
 * @param version the version it was saved at
 */
public record CustomerSnapshot(
        CustomerId id,
        Optional<ContactName> name,
        Optional<PhoneNumber> phone,
        Optional<Address> address,
        Optional<DriverNote> note,
        Location location,
        Set<AddressFlag> flags,
        OrderDefaults defaults,
        Customer.State state,
        Instant createdAt,
        Version version)
        implements PersonalData {

    /** Checks every part is given, and copies the flags in their declared order. */
    public CustomerSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(version, "version");
        EnumSet<AddressFlag> copied = EnumSet.noneOf(AddressFlag.class);
        copied.addAll(flags);
        flags = Collections.unmodifiableSet(copied);
    }

    /**
     * A form that hides the customer, because it holds personal data.
     *
     * @return {@code CustomerSnapshot[redacted]}
     */
    @Override
    public String toString() {
        return "CustomerSnapshot[redacted]";
    }
}
