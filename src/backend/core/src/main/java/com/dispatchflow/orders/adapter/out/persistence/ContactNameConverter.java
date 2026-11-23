package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link ContactName} in its normal form, rebuilt through the rule that checked it. */
@Converter(autoApply = true)
public final class ContactNameConverter extends StoredValueConverter<ContactName, String> {

    /** Creates the converter, which Hibernate does once. */
    public ContactNameConverter() {
        super(ContactName.class, ContactName::text, ContactName::of);
    }
}
