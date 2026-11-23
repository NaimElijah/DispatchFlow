package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.time.Duration;

/**
 * Stores a {@link SolveLimit} as its nanoseconds, in a {@code bigint} column, as every duration is
 * stored (ADR-127). A stored limit outside 5 to 60 whole seconds is refused when read.
 */
@Converter(autoApply = true)
public final class SolveLimitConverter extends StoredValueConverter<SolveLimit, Long> {

    /** Creates the converter, which Hibernate does once. */
    public SolveLimitConverter() {
        super(SolveLimit.class, limit -> limit.duration().toNanos(), nanos -> new SolveLimit(Duration.ofNanos(nanos)));
    }
}
