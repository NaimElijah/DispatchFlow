package com.dispatchflow.shared.adapter.out.persistence.id;

import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * Stores a {@link CustomerId} that points to another aggregate as its {@code uuid}. A root's own
 * key is a plain {@code UUID} field, which no converter touches (ADR-126).
 */
@Converter(autoApply = true)
public final class CustomerIdConverter extends StoredValueConverter<CustomerId, UUID> {

    /** Creates the converter, which Hibernate does once. */
    public CustomerIdConverter() {
        super(CustomerId.class, CustomerId::value, CustomerId::of);
    }
}
