package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.Floor;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link Floor} as its text, rebuilt through {@code Floor.ofStored}. */
@Converter(autoApply = true)
public final class FloorConverter extends StoredValueConverter<Floor, String> {

    /** Creates the converter, which Hibernate does once. */
    public FloorConverter() {
        super(Floor.class, Floor::text, Floor::ofStored);
    }
}
