package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.account.PasswordHash;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link PasswordHash} in the hashing adapter's own encoding (NFR-36). */
@Converter(autoApply = true)
public final class PasswordHashConverter extends StoredValueConverter<PasswordHash, String> {

    /** Creates the converter, which Hibernate does once. */
    public PasswordHashConverter() {
        super(PasswordHash.class, PasswordHash::value, PasswordHash::new);
    }
}
