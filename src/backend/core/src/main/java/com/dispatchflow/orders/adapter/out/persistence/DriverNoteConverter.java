package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link DriverNote} as its text, rebuilt through {@code DriverNote.ofStored}. */
@Converter(autoApply = true)
public final class DriverNoteConverter extends StoredValueConverter<DriverNote, String> {

    /** Creates the converter, which Hibernate does once. */
    public DriverNoteConverter() {
        super(DriverNote.class, DriverNote::text, DriverNote::ofStored);
    }
}
