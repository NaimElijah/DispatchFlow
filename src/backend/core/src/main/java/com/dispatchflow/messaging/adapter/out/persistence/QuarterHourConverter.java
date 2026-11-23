package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.QuarterHour;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.time.Instant;

/** Stores a {@link QuarterHour} as its instant, in a {@code timestamptz} column. */
@Converter(autoApply = true)
public final class QuarterHourConverter extends StoredValueConverter<QuarterHour, Instant> {

    /** Creates the converter, which Hibernate does once. */
    public QuarterHourConverter() {
        super(QuarterHour.class, QuarterHour::at, QuarterHour::new);
    }
}
