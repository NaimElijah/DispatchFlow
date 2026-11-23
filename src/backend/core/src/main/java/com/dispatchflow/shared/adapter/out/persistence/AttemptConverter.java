package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.Attempt;
import jakarta.persistence.Converter;

/** Stores an {@link Attempt} as its number, from 1. */
@Converter(autoApply = true)
public final class AttemptConverter extends StoredValueConverter<Attempt, Integer> {

    /** Creates the converter, which Hibernate does once. */
    public AttemptConverter() {
        super(Attempt.class, Attempt::number, Attempt::of);
    }
}
