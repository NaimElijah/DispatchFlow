package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.fleet.domain.roster.RosterEntry;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;

/**
 * One date's roster as its screen shows it (FR-54, FR-79): each driver's shift, vehicle and
 * availability, and the vehicles marked unavailable. Each entry's vehicle tells a value a fill may
 * still set from one a dispatcher chose.
 *
 * <p>Built in M2.
 *
 * @param date the roster's date
 * @param entries every rostered driver, each once
 * @param unavailableVehicles the vehicles marked unavailable that date (FR-07, FR-301)
 */
public record RosterView(DeliveryDate date, List<RosterEntry> entries, List<VehicleId> unavailableVehicles) {

    /** Checks every part is present, and copies the lists. */
    public RosterView {
        Objects.requireNonNull(date, "date");
        entries = List.copyOf(entries);
        unavailableVehicles = List.copyOf(unavailableVehicles);
    }
}
