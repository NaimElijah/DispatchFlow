package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.driver.DriverName;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link DriverName} as its text, rebuilt through {@code DriverName.ofStored}. */
@Converter(autoApply = true)
public final class DriverNameConverter extends StoredValueConverter<DriverName, String> {

    /** Creates the converter, which Hibernate does once. */
    public DriverNameConverter() {
        super(DriverName.class, DriverName::value, DriverName::ofStored);
    }
}
