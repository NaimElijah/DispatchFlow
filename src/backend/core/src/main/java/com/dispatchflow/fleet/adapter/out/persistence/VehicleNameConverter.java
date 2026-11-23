package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.register.VehicleName;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link VehicleName} as its text, as entered and normalized, rebuilt through {@code
 * VehicleName.ofStored}. BR-48's matching form is never stored: the register derives it.
 */
@Converter(autoApply = true)
public final class VehicleNameConverter extends StoredValueConverter<VehicleName, String> {

    /** Creates the converter, which Hibernate does once. */
    public VehicleNameConverter() {
        super(VehicleName.class, VehicleName::value, VehicleName::ofStored);
    }
}
