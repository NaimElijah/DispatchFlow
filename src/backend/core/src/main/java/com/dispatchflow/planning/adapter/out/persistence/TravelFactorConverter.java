package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.settings.TravelFactor;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/**
 * Stores a {@link TravelFactor} as its value, in a {@code numeric} column. A stored factor of zero or
 * less is refused when read.
 */
@Converter(autoApply = true)
public final class TravelFactorConverter extends StoredValueConverter<TravelFactor, BigDecimal> {

    /** Creates the converter, which Hibernate does once. */
    public TravelFactorConverter() {
        super(TravelFactor.class, TravelFactor::value, TravelFactor::of);
    }
}
