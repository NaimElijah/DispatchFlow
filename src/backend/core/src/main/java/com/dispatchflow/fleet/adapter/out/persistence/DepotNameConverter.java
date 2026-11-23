package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.register.DepotName;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link DepotName} as its text, rebuilt through {@code DepotName.ofStored}. */
@Converter(autoApply = true)
public final class DepotNameConverter extends StoredValueConverter<DepotName, String> {

    /** Creates the converter, which Hibernate does once. */
    public DepotNameConverter() {
        super(DepotName.class, DepotName::value, DepotName::ofStored);
    }
}
