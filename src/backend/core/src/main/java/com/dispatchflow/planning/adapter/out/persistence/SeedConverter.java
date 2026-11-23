package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.plan.Seed;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link Seed} as its value, in a {@code bigint} column.
 */
@Converter(autoApply = true)
public final class SeedConverter extends StoredValueConverter<Seed, Long> {

    /** Creates the converter, which Hibernate does once. */
    public SeedConverter() {
        super(Seed.class, Seed::value, Seed::of);
    }
}
