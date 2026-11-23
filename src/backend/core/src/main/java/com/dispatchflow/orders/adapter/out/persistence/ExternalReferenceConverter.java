package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.ExternalReference;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores an {@link ExternalReference} as its text, rebuilt through {@code ExternalReference.ofStored}. */
@Converter(autoApply = true)
public final class ExternalReferenceConverter extends StoredValueConverter<ExternalReference, String> {

    /** Creates the converter, which Hibernate does once. */
    public ExternalReferenceConverter() {
        super(ExternalReference.class, ExternalReference::text, ExternalReference::ofStored);
    }
}
