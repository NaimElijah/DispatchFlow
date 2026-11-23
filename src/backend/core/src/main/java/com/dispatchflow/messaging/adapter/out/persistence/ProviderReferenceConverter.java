package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.message.ProviderReference;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores the SMS provider's {@link ProviderReference} for an accepted message as its text. */
@Converter(autoApply = true)
public final class ProviderReferenceConverter extends StoredValueConverter<ProviderReference, String> {

    /** Creates the converter, which Hibernate does once. */
    public ProviderReferenceConverter() {
        super(ProviderReference.class, ProviderReference::value, ProviderReference::new);
    }
}
