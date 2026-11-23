package com.dispatchflow.shared.adapter.out.persistence;

import jakarta.persistence.Converter;
import java.time.Duration;

/**
 * Stores every {@link Duration} as its nanoseconds, in a {@code bigint} column, so that each
 * module stores a duration the same way and exactly, whatever Hibernate's own default for the type
 * is (ADR-128). A {@code bigint} holds about 292 years.
 */
@Converter(autoApply = true)
public final class DurationConverter extends StoredValueConverter<Duration, Long> {

    /** Creates the converter, which Hibernate does once. */
    public DurationConverter() {
        super(Duration.class, Duration::toNanos, Duration::ofNanos);
    }
}
