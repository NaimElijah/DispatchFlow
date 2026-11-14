package com.dispatchflow.fleet.domain;

import com.dispatchflow.fleet.domain.FleetError.BlankDepotName;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * A depot's name, as dispatchers see it (FR-04, FR-56). It is trimmed.
 *
 * @param value the trimmed name
 */
public record DepotName(String value) {

    /**
     * Trims and checks the name.
     *
     * @throws RuleViolation with {@link BlankDepotName} when nothing is left after trimming
     */
    public DepotName {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new RuleViolation(new BlankDepotName());
        }
    }

    /**
     * Reads a depot name.
     *
     * @param text the name as typed
     * @return the trimmed name
     * @throws RuleViolation with {@link BlankDepotName} when nothing is left after trimming
     */
    public static DepotName of(String text) {
        return new DepotName(text);
    }
}
