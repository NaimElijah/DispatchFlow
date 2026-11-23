package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.Priority;
import jakarta.persistence.Converter;

/** Stores a {@link Priority} as its level. */
@Converter(autoApply = true)
public final class PriorityConverter extends StoredValueConverter<Priority, Integer> {

    /** Creates the converter, which Hibernate does once. */
    public PriorityConverter() {
        super(Priority.class, Priority::level, Priority::of);
    }
}
