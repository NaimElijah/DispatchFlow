package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.Objects;

/**
 * The roster's answer for one vehicle and driver on the plan's date: whether the vehicle is
 * available and the driver is its driver with the Driver role (BR-20, FR-194, ADR-062).
 *
 * @param vehicle the vehicle
 * @param driver the driver asked about
 * @param assignable whether the pair meets BR-20
 */
public record RouteStaffing(VehicleId vehicle, AccountId driver, boolean assignable) {

    /** Checks both IDs are present. */
    public RouteStaffing {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
    }
}
