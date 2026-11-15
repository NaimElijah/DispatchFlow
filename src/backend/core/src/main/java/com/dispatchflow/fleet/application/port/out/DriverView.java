package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.driver.DriverContact;
import com.dispatchflow.fleet.domain.driver.DriverStatus;
import com.dispatchflow.fleet.domain.driver.WeeklyShifts;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;

/**
 * One driver record as the drivers screen shows it (FR-06, FR-56, FR-79, FR-132, FR-189). The
 * version is the one every change to the record sends back (BR-76). It holds the driver's name and
 * phone, so it prints redacted (NFR-19).
 *
 * <p>Built in M2.
 *
 * @param driver the driver's account
 * @param status active, retired or anonymized (FR-56, FR-193)
 * @param contact the name and the contact phone the web app shows beside their route; empty once
 *     anonymized (FR-132)
 * @param defaultShift the shift hours a date gets by default (FR-06, FR-189)
 * @param defaultVehicle the vehicle a date gets by default (FR-06, FR-189)
 * @param weeklyShifts the weekly template, possibly empty (FR-79)
 * @param version the version the record was read at, for {@code If-Match} (BR-76)
 */
public record DriverView(
        AccountId driver,
        DriverStatus status,
        Optional<DriverContact> contact,
        ShiftHours defaultShift,
        VehicleId defaultVehicle,
        WeeklyShifts weeklyShifts,
        Version version)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public DriverView {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(contact, "contact");
        Objects.requireNonNull(defaultShift, "defaultShift");
        Objects.requireNonNull(defaultVehicle, "defaultVehicle");
        Objects.requireNonNull(weeklyShifts, "weeklyShifts");
        Objects.requireNonNull(version, "version");
    }

    /**
     * A form that hides the name and the phone.
     *
     * @return {@code DriverView[redacted]}
     */
    @Override
    public String toString() {
        return "DriverView[redacted]";
    }
}
