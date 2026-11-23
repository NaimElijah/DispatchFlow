package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.register.Plate;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link Plate} in its normalized form, rebuilt through {@code Plate.of}. Normalizing a
 * normalized plate changes nothing, so the input rule accepts every plate it stored, and a plate
 * needs no {@code ofStored} of its own; a stored text the rule refuses is an incident.
 */
@Converter(autoApply = true)
public final class PlateConverter extends StoredValueConverter<Plate, String> {

    /** Creates the converter, which Hibernate does once. */
    public PlateConverter() {
        super(Plate.class, Plate::value, Plate::of);
    }
}
