package com.dispatchflow.reporting.domain.baseline;

import com.dispatchflow.reporting.domain.ReportingError.NegativeDriverHours;
import com.dispatchflow.shared.RuleViolation;
import java.time.Duration;
import java.util.Objects;

/**
 * The time a plan's drivers are planned to work, summed over its routes (FR-39). It is kept exact;
 * only a screen converts it to hours and rounds (ADR-109).
 *
 * @param value the time, zero or more
 */
public record DriverHours(Duration value) {

    /**
     * Checks the time.
     *
     * @throws RuleViolation with {@link NegativeDriverHours} when {@code value} is negative
     */
    public DriverHours {
        Objects.requireNonNull(value, "value");
        if (value.isNegative()) {
            throw new RuleViolation(new NegativeDriverHours(value.getSeconds()));
        }
    }

    /**
     * Creates the time.
     *
     * @param value the time
     * @return the driver-hours
     * @throws RuleViolation with {@link NegativeDriverHours} when {@code value} is negative
     */
    public static DriverHours of(Duration value) {
        return new DriverHours(value);
    }
}
