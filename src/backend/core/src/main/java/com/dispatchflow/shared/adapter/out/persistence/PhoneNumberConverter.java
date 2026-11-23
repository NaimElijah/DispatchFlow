package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.PhoneNumber;
import jakarta.persistence.Converter;

/** Stores a {@link PhoneNumber} as its E.164 text, rebuilt through {@code PhoneNumber.ofStored}. */
@Converter(autoApply = true)
public final class PhoneNumberConverter extends StoredValueConverter<PhoneNumber, String> {

    /** Creates the converter, which Hibernate does once. */
    public PhoneNumberConverter() {
        super(PhoneNumber.class, PhoneNumber::e164, PhoneNumber::ofStored);
    }
}
