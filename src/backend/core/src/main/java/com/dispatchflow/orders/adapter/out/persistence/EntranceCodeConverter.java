package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.EntranceCode;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores an {@link EntranceCode} as its text, rebuilt through {@code EntranceCode.ofStored}. */
@Converter(autoApply = true)
public final class EntranceCodeConverter extends StoredValueConverter<EntranceCode, String> {

    /** Creates the converter, which Hibernate does once. */
    public EntranceCodeConverter() {
        super(EntranceCode.class, EntranceCode::text, EntranceCode::ofStored);
    }
}
