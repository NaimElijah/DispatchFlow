package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.alert.DedupeKey;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores an alert's {@link DedupeKey} as its text, which the unique constraint reads (DL §5). */
@Converter(autoApply = true)
public final class DedupeKeyConverter extends StoredValueConverter<DedupeKey, String> {

    /** Creates the converter, which Hibernate does once. */
    public DedupeKeyConverter() {
        super(DedupeKey.class, DedupeKey::value, DedupeKey::new);
    }
}
