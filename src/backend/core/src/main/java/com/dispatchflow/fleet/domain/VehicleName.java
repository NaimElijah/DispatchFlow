package com.dispatchflow.fleet.domain;

import com.dispatchflow.fleet.domain.FleetError.BlankVehicleName;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * A vehicle's display name, which identifies it on the map, the timeline, the phone app and
 * printouts (FR-131, FR-198). It is trimmed, and two names are the same when their trimmed text is
 * equal; BR-48 keeps it unique among active vehicles.
 *
 * @param value the trimmed name
 */
public record VehicleName(String value) {

    /**
     * Trims and checks the name.
     *
     * @throws RuleViolation with {@link BlankVehicleName} when nothing is left after trimming
     */
    public VehicleName {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new RuleViolation(new BlankVehicleName());
        }
    }

    /**
     * Reads a display name.
     *
     * @param text the name as typed
     * @return the trimmed name
     * @throws RuleViolation with {@link BlankVehicleName} when nothing is left after trimming
     */
    public static VehicleName of(String text) {
        return new VehicleName(text);
    }
}
