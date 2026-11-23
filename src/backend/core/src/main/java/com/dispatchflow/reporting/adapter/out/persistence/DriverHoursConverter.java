package com.dispatchflow.reporting.adapter.out.persistence;

import com.dispatchflow.reporting.domain.baseline.DriverHours;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.time.Duration;

/**
 * Stores planned {@link DriverHours} as a whole number of nanoseconds, so the time comes back
 * exactly as it was given, a fraction of a second included (ADR-109). A {@code long} of nanoseconds
 * holds over 290 years, far beyond any day's driver-hours.
 */
@Converter(autoApply = true)
public final class DriverHoursConverter extends StoredValueConverter<DriverHours, Long> {

    /** Creates the converter, which Hibernate does once. */
    public DriverHoursConverter() {
        super(DriverHours.class, hours -> hours.value().toNanos(), nanos -> DriverHours.of(Duration.ofNanos(nanos)));
    }
}
