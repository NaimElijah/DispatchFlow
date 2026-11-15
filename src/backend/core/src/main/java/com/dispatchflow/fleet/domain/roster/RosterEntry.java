package com.dispatchflow.fleet.domain.roster;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.Objects;
import java.util.Optional;

/**
 * One driver on one date's roster (FR-54): their shift, their vehicle, and whether they are
 * available. {@link DayRoster} keeps one per driver and gives each vehicle to at most one. An entry
 * always has a shift, because it is created only by setting one or by a fill.
 *
 * @param driver the driver
 * @param shift their shift on the roster's date
 * @param vehicle the vehicle they drive, no vehicle, or not set yet
 * @param availability whether they work as planned
 */
public record RosterEntry(AccountId driver, Shift shift, RosterVehicle vehicle, Availability availability) {

    /** Checks every field is present. */
    public RosterEntry {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(shift, "shift");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(availability, "availability");
    }

    RosterEntry withShift(Shift newShift) {
        return new RosterEntry(driver, newShift, vehicle, availability);
    }

    RosterEntry withVehicle(RosterVehicle newVehicle) {
        return new RosterEntry(driver, shift, newVehicle, availability);
    }

    RosterEntry withAvailability(Availability newAvailability) {
        return new RosterEntry(driver, shift, vehicle, newAvailability);
    }

    Optional<VehicleId> drivenVehicle() {
        return vehicle.driven();
    }

    boolean drives(VehicleId candidate) {
        return drivenVehicle().filter(candidate::equals).isPresent();
    }
}
