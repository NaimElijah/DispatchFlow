package com.dispatchflow.fleet.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.VehicleId;
import java.util.Objects;
import java.util.Optional;

/**
 * One driver on one date's roster (FR-54, FR-190): their shift, their vehicle, whether they are
 * available, and their standing, which Fleet combines from Access's answer on the account and the
 * driver record's state (ADR-062). Planning builds its {@code RouteStaffing}, {@code
 * VehicleStanding} and {@code DriverStanding} from it; Live Operations its {@code StartDeadline}
 * from the shift's start and its {@code TrackingStanding} from the availability and the shift's end
 * (FR-101, FR-328, BR-20).
 *
 * <p>To Be Implemented.
 *
 * @param driver the driver's account
 * @param shift their shift on the date, as Israel times on that date
 * @param vehicle the vehicle they drive that date, or empty when they have none
 * @param available whether they work as planned, not marked unavailable (FR-82, FR-300)
 * @param standing whether they may be planned now, and why not (ADR-062, FR-193); {@link
 *     FleetFacts#rosterOn} gives only {@link DriverStandingFact#ACTIVE} drivers
 */
public record RosteredDriver(
        AccountId driver,
        TimeWindow shift,
        Optional<VehicleId> vehicle,
        boolean available,
        DriverStandingFact standing) {

    /** Checks every part is present, possibly empty. */
    public RosteredDriver {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(shift, "shift");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(standing, "standing");
    }
}
