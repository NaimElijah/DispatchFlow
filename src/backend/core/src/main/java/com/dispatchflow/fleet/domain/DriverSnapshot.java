package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;

/**
 * A driver's stored state: what {@link Driver#snapshot()} returns and {@link Driver#restore}
 * rebuilds from.
 *
 * @param id the driver's account, which keys the driver
 * @param version the version it was loaded at
 * @param status active, retired or anonymized
 * @param contact the driver's name and phone; empty only once anonymized
 * @param defaultShift the shift hours a date gets by default (FR-06, FR-189)
 * @param defaultVehicle the vehicle a date gets by default (FR-06, FR-189)
 * @param weeklyShifts the weekly template, which may be empty (FR-79)
 */
public record DriverSnapshot(
        AccountId id,
        Version version,
        DriverStatus status,
        Optional<DriverContact> contact,
        ShiftHours defaultShift,
        VehicleId defaultVehicle,
        WeeklyShifts weeklyShifts) {

    /** Checks every field is present. */
    public DriverSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(contact, "contact");
        Objects.requireNonNull(defaultShift, "defaultShift");
        Objects.requireNonNull(defaultVehicle, "defaultVehicle");
        Objects.requireNonNull(weeklyShifts, "weeklyShifts");
    }
}
