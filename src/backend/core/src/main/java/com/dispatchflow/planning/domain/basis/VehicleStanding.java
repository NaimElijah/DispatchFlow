package com.dispatchflow.planning.domain.basis;

import com.dispatchflow.shared.VehicleId;
import java.util.Objects;

/**
 * Whether a vehicle still meets BR-20 with the driver a proposal gives it, when the proposal is
 * accepted (BR-64, ADR-055).
 *
 * @param vehicle the vehicle
 * @param meetsBr20 whether it is available and its driver holds the Driver role and is its driver
 */
public record VehicleStanding(VehicleId vehicle, boolean meetsBr20) {

    /** Checks the vehicle is present. */
    public VehicleStanding {
        Objects.requireNonNull(vehicle, "vehicle");
    }
}
