package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.DeliveryDate;
import jakarta.persistence.Converter;
import java.time.LocalDate;

/** Stores a {@link DeliveryDate} as a {@code date} column. */
@Converter(autoApply = true)
public final class DeliveryDateConverter extends StoredValueConverter<DeliveryDate, LocalDate> {

    /** Creates the converter, which Hibernate does once. */
    public DeliveryDateConverter() {
        super(DeliveryDate.class, DeliveryDate::value, DeliveryDate::of);
    }
}
