package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores an {@link Address} as it was typed, rebuilt through the rule that checked it. */
@Converter(autoApply = true)
public final class AddressConverter extends StoredValueConverter<Address, String> {

    /** Creates the converter, which Hibernate does once. */
    public AddressConverter() {
        super(Address.class, Address::text, Address::of);
    }
}
