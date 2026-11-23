package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.Weight;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/** Stores a {@link Weight} as its kilograms, in a {@code numeric} column. */
@Converter(autoApply = true)
public final class WeightConverter extends StoredValueConverter<Weight, BigDecimal> {

    /** Creates the converter, which Hibernate does once. */
    public WeightConverter() {
        super(Weight.class, Weight::kilograms, Weight::kilograms);
    }
}
