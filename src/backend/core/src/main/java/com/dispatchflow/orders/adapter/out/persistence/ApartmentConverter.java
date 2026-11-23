package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.Apartment;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores an {@link Apartment} as its text, rebuilt through {@code Apartment.ofStored}. */
@Converter(autoApply = true)
public final class ApartmentConverter extends StoredValueConverter<Apartment, String> {

    /** Creates the converter, which Hibernate does once. */
    public ApartmentConverter() {
        super(Apartment.class, Apartment::text, Apartment::ofStored);
    }
}
