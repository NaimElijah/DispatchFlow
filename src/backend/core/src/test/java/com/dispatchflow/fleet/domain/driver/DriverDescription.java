package com.dispatchflow.fleet.domain.driver;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.Optional;

/**
 * What a test sees of a {@link Driver}, read through its getters: a value that compares by every
 * part, which the entity doesn't. Tests compare a driver before and after a command, or with the
 * driver they expect, through it.
 *
 * @param id the driver's account
 * @param status active, retired or anonymized
 * @param contact the name and phone, until anonymized
 * @param defaultShift the shift hours a date gets by default
 * @param defaultVehicle the vehicle a date gets by default
 * @param weeklyShifts the weekly template
 */
public record DriverDescription(
        AccountId id,
        DriverStatus status,
        Optional<DriverContact> contact,
        ShiftHours defaultShift,
        VehicleId defaultVehicle,
        WeeklyShifts weeklyShifts) {

    /**
     * Describes a driver.
     *
     * @param driver the driver
     * @return what its getters give
     */
    public static DriverDescription of(Driver driver) {
        return new DriverDescription(
                driver.id(),
                driver.status(),
                driver.contact(),
                driver.defaultShift(),
                driver.defaultVehicle(),
                driver.weeklyShifts());
    }
}
