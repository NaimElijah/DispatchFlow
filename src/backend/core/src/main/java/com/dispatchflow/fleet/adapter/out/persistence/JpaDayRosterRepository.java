package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.domain.roster.DayRoster;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Keeps the day rosters in {@code fleet.day_roster}, one per date, with their entries in {@code
 * fleet.roster_entry} and their unavailable vehicles in {@code fleet.unavailable_vehicle}.
 *
 * <p>A roster loads whole: its row with its entries, then its unavailable vehicles in a second query
 * into the same persistence context, so the two lists never multiply each other's rows.
 */
@Component
class JpaDayRosterRepository implements DayRosterRepository {

    private final DayRosters rosters;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param rosters the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaDayRosterRepository(DayRosters rosters, VersionedSave versionedSave) {
        this.rosters = Objects.requireNonNull(rosters, "rosters");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public Optional<DayRoster> get(DeliveryDate date) {
        // The second query answers the instance the first one loaded, now with its vehicles too.
        return rosters.findByDate(date).flatMap(withEntries -> rosters.withUnavailableVehicles(date));
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which turns a first save that breaks the date's unique
     * constraint, because another call stored that date's roster first, into {@code StaleVersion}.
     */
    @Override
    public void add(DayRoster roster) {
        versionedSave.save(roster, rosters::save);
    }

    @Override
    public void save(DayRoster roster) {
        versionedSave.save(roster, rosters::save);
    }
}
