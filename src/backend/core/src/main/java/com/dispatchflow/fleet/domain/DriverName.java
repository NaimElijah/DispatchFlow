package com.dispatchflow.fleet.domain;

import com.dispatchflow.fleet.domain.FleetError.BlankDriverName;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * A driver's name (DL §4.2). It is personal data (NFR-19), so {@link #toString()} hides it and no
 * error or event carries it; it is removed when the driver is anonymized (BR-98).
 *
 * @param value the trimmed name
 */
public record DriverName(String value) {

    /**
     * Trims and checks the name.
     *
     * @throws RuleViolation with {@link BlankDriverName} when nothing is left after trimming
     */
    public DriverName {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new RuleViolation(new BlankDriverName());
        }
    }

    /**
     * Reads a driver's name.
     *
     * @param text the name as typed
     * @return the trimmed name
     * @throws RuleViolation with {@link BlankDriverName} when nothing is left after trimming
     */
    public static DriverName of(String text) {
        return new DriverName(text);
    }

    /**
     * A form that hides the name, because it is personal data.
     *
     * @return {@code DriverName[redacted]}
     */
    @Override
    public String toString() {
        return "DriverName[redacted]";
    }
}
