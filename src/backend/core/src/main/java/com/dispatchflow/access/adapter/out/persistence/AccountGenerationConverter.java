package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores an {@link AccountGeneration} as its count, which the token check compares (NFR-177). */
@Converter(autoApply = true)
public final class AccountGenerationConverter extends StoredValueConverter<AccountGeneration, Long> {

    /** Creates the converter, which Hibernate does once. */
    public AccountGenerationConverter() {
        super(AccountGeneration.class, AccountGeneration::value, AccountGeneration::of);
    }
}
