package com.dispatchflow.reporting.adapter.out.persistence;

import com.dispatchflow.reporting.domain.baseline.Distance;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a planned {@link Distance} as its whole metres, exact (ADR-109). */
@Converter(autoApply = true)
public final class DistanceConverter extends StoredValueConverter<Distance, Long> {

    /** Creates the converter, which Hibernate does once. */
    public DistanceConverter() {
        super(Distance.class, Distance::metres, Distance::of);
    }
}
