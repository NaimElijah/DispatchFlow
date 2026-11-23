package com.dispatchflow.fleet.domain.roster;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One date's roster as stored: what {@link DayRoster#snapshot()} returns and {@link
 * DayRoster#restore} rebuilds from.
 *
 * <p>The persistence adapter builds every stored value inside {@link
 * com.dispatchflow.shared.StoredStateInvalid#whileRestoring}, so a value that the rules now refuse
 * is an incident, never a user-facing refusal. Here that covers every shift: a change to the
 * time-zone rules can move a stored shift into an hour the clock skips, which {@link Shift}
 * refuses.
 *
 * @param date the delivery date, which keys the roster
 * @param version the version it was loaded at, or null for an aggregate never saved
 *     (ADR-126)
 * @param entries one per rostered driver, in the order they were rostered
 * @param unavailableVehicles the vehicles marked unavailable that date, in the order they were
 *     marked (FR-07, FR-301)
 */
public record DayRosterSnapshot(
        DeliveryDate date, @Nullable Version version, List<RosterEntry> entries, List<VehicleId> unavailableVehicles) {

    /** Copies the lists. */
    public DayRosterSnapshot {
        Objects.requireNonNull(date, "date");
        entries = List.copyOf(entries);
        unavailableVehicles = List.copyOf(unavailableVehicles);
    }
}
