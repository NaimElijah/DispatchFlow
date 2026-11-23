package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.PhoneHash;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a phone's keyed {@link PhoneHash} as its encoded text (NFR-202). */
@Converter(autoApply = true)
public final class PhoneHashConverter extends StoredValueConverter<PhoneHash, String> {

    /** Creates the converter, which Hibernate does once. */
    public PhoneHashConverter() {
        super(PhoneHash.class, PhoneHash::value, PhoneHash::new);
    }
}
