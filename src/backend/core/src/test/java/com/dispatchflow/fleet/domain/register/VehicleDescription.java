package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.Optional;

/**
 * What a test sees of one {@link Vehicle}, read through its getters: a value that compares by every
 * part, which the entity doesn't. A builder also lists the vehicles it wants as these.
 *
 * @param id the vehicle
 * @param details its name, plate, capacity and depots
 * @param retiredAt when it was retired, or empty while it is active
 */
public record VehicleDescription(VehicleId id, VehicleDetails details, Optional<Instant> retiredAt) {

    /**
     * Describes a vehicle.
     *
     * @param vehicle the vehicle
     * @return what its getters give
     */
    public static VehicleDescription of(Vehicle vehicle) {
        return new VehicleDescription(vehicle.id(), vehicle.details(), vehicle.retiredAt());
    }
}
