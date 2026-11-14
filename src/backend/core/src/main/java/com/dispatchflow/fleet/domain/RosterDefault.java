package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.Objects;

/**
 * What one driver's records give a date by default: their shift hours and their default vehicle
 * (FR-06, FR-79, FR-189, ADR-103), with whether the driver may be rostered. {@link
 * Driver#rosterDefaultOn} builds it, and {@link DayRoster#fill} uses it for the values a dispatcher
 * has not set.
 *
 * @param driver the driver
 * @param hours the shift hours for that date's weekday, or the default shift
 * @param vehicle the driver's default vehicle
 * @param standing Access's answer on the account combined with the driver record's state (ADR-062):
 *     a fill skips a driver who is not {@link Standing#ACTIVE}
 */
public record RosterDefault(AccountId driver, ShiftHours hours, VehicleId vehicle, Standing standing) {

    /** Checks every field is present. */
    public RosterDefault {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(standing, "standing");
    }
}
