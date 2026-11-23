package com.dispatchflow.fleet.domain.roster;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import java.util.List;

/**
 * What a test sees of a {@link DayRoster}, read through its getters: a value that compares by every
 * part, which the entity doesn't. Tests compare a roster before and after a command, or with the
 * roster a builder describes, through it.
 *
 * @param date the delivery date
 * @param entries one per rostered driver, in the order they were rostered
 * @param unavailableVehicles the vehicles marked unavailable, in the order they were marked
 */
public record RosterDescription(DeliveryDate date, List<RosterEntry> entries, List<VehicleId> unavailableVehicles) {

    /** Copies the lists. */
    public RosterDescription {
        entries = List.copyOf(entries);
        unavailableVehicles = List.copyOf(unavailableVehicles);
    }

    /**
     * Describes a roster.
     *
     * @param roster the roster
     * @return what its getters give
     */
    public static RosterDescription of(DayRoster roster) {
        return new RosterDescription(roster.date(), roster.entries(), roster.unavailableVehicles());
    }
}
