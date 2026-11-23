package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link SolverName} as its name, in a {@code text} column.
 */
@Converter(autoApply = true)
public final class SolverNameConverter extends StoredValueConverter<SolverName, String> {

    /** Creates the converter, which Hibernate does once. */
    public SolverNameConverter() {
        super(SolverName.class, SolverName::value, SolverName::of);
    }
}
