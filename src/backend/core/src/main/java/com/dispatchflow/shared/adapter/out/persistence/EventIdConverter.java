package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.EventId;
import jakarta.persistence.Converter;

/** Stores an {@link EventId} as its canonical text, {@code type:aggregate:version:ordinal}. */
@Converter(autoApply = true)
public final class EventIdConverter extends StoredValueConverter<EventId, String> {

    /** Creates the converter, which Hibernate does once. */
    public EventIdConverter() {
        super(EventId.class, EventId::toText, EventId::parse);
    }
}
