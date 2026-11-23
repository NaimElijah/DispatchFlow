package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.fleet.application.api.DriverStandingFact;
import com.dispatchflow.fleet.domain.roster.RosterEntry;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One date's roster as its screen shows it (FR-54, FR-79): each driver's shift, vehicle,
 * availability and standing, and the vehicles marked unavailable. Each entry's vehicle tells a
 * value a fill may still set from one a dispatcher chose; its standing tells the dispatcher why a
 * driver on the roster won't be planned (ADR-062, FR-193). The version is the one every roster
 * change that takes a version sends back as {@code If-Match} (BR-76); {@code FillDayRoster} sends
 * none.
 *
 * <p>Built in M2.
 *
 * @param date the roster's date
 * @param entries every rostered driver, each once, whatever their standing
 * @param unavailableVehicles the vehicles marked unavailable that date (FR-07, FR-301)
 * @param version the version the roster was read at, for {@code If-Match} on every roster change
 *     but {@code FillDayRoster}, which takes none (BR-76); empty for a date nobody
 *     rostered, which the first change opens
 */
public record RosterView(
        DeliveryDate date, List<Line> entries, List<VehicleId> unavailableVehicles, Optional<Version> version) {

    /** Checks every part is present, and copies the lists. */
    public RosterView {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(version, "version");
        entries = List.copyOf(entries);
        unavailableVehicles = List.copyOf(unavailableVehicles);
    }

    /**
     * One rostered driver and their standing.
     *
     * <p>Built in M2.
     *
     * @param entry the driver's shift, vehicle and availability
     * @param standing whether they may be planned now, and why not (ADR-062)
     */
    public record Line(RosterEntry entry, DriverStandingFact standing) {

        /** Checks both parts are present. */
        public Line {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(standing, "standing");
        }
    }
}
