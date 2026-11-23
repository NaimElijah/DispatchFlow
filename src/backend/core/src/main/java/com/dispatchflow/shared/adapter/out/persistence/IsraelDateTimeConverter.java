package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.IsraelDateTime;
import jakarta.persistence.Converter;
import java.time.LocalDateTime;

/** Stores an {@link IsraelDateTime} as a {@code timestamp} column, Israel's local time (ADR-088). */
@Converter(autoApply = true)
public final class IsraelDateTimeConverter extends StoredValueConverter<IsraelDateTime, LocalDateTime> {

    /** Creates the converter, which Hibernate does once. */
    public IsraelDateTimeConverter() {
        super(IsraelDateTime.class, IsraelDateTime::localDateTime, IsraelDateTime::of);
    }
}
