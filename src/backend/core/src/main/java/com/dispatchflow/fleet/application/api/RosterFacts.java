package com.dispatchflow.fleet.application.api;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;

/**
 * One date's roster as other modules read it (DL §2.2): who drives that date, with
 * which vehicle and shift, and which vehicles are marked unavailable (FR-54, FR-194).
 *
 * <p>Built in M2.
 *
 * @param date the roster's date
 * @param drivers every rostered driver, each once
 * @param unavailableVehicles the vehicles marked unavailable that date (FR-194)
 */
public record RosterFacts(DeliveryDate date, List<RosteredDriver> drivers, List<VehicleId> unavailableVehicles) {

    /** Checks the date is present, and copies the lists. */
    public RosterFacts {
        Objects.requireNonNull(date, "date");
        drivers = List.copyOf(drivers);
        unavailableVehicles = List.copyOf(unavailableVehicles);
    }
}
