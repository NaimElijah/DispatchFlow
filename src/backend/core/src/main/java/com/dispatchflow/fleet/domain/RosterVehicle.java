package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.VehicleId;
import java.util.Objects;
import java.util.Optional;

/**
 * The vehicle of one driver on one date's roster, in three states, so a dispatcher's "no vehicle"
 * differs from a value nobody set yet. A driver's defaults fill only what is {@link NotSet}
 * (FR-79, ADR-103).
 */
public sealed interface RosterVehicle {

    /** Nobody chose yet, so a fill may give the driver their default vehicle. */
    record NotSet() implements RosterVehicle {}

    /** A dispatcher chose no vehicle, or the driver's vehicle went with a handed-over route. */
    record NoVehicle() implements RosterVehicle {}

    /**
     * The driver drives this vehicle on the date.
     *
     * @param vehicle the vehicle
     */
    record Assigned(VehicleId vehicle) implements RosterVehicle {

        /** Checks the vehicle is present. */
        public Assigned {
            Objects.requireNonNull(vehicle, "vehicle");
        }
    }

    /**
     * The vehicle, if the driver drives one.
     *
     * @return the vehicle of an {@link Assigned} state, or empty
     */
    default Optional<VehicleId> driven() {
        return this instanceof Assigned assigned ? Optional.of(assigned.vehicle()) : Optional.empty();
    }
}
