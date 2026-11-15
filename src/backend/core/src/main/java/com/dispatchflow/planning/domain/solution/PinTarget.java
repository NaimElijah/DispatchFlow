package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.Objects;

/** What an order is pinned to: a vehicle or a driver (FR-91). */
public sealed interface PinTarget {

    /**
     * The order is served by this vehicle or dropped (FR-91, FR-228).
     *
     * @param vehicle the vehicle
     */
    record ToVehicle(VehicleId vehicle) implements PinTarget {

        /** Checks the vehicle is present. */
        public ToVehicle {
            Objects.requireNonNull(vehicle, "vehicle");
        }
    }

    /**
     * The order is served by this driver or dropped (FR-91, FR-228).
     *
     * @param driver the driver's account
     */
    record ToDriver(AccountId driver) implements PinTarget {

        /** Checks the driver is present. */
        public ToDriver {
            Objects.requireNonNull(driver, "driver");
        }
    }
}
