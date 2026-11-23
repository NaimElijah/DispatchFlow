package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.settings.DepartureAllowance;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link DepartureAllowance} as its whole minutes, in an {@code integer} column. A stored
 * negative allowance is refused when read.
 */
@Converter(autoApply = true)
public final class DepartureAllowanceConverter extends StoredValueConverter<DepartureAllowance, Integer> {

    /** Creates the converter, which Hibernate does once. */
    public DepartureAllowanceConverter() {
        super(DepartureAllowance.class, DepartureAllowance::minutes, DepartureAllowance::ofMinutes);
    }
}
