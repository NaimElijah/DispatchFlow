package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.domain.roster.DayRoster;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the day rosters in {@code fleet.day_roster}, one per date, with their entries in {@code
 * fleet.roster_entry} and their unavailable vehicles in {@code fleet.unavailable_vehicle}.
 *
 * <p>A roster loads whole: its row, then its entries and its unavailable vehicles, each list in a
 * query of its own, so the two lists never multiply each other's rows.
 */
@Repository
@Transactional
class JpaDayRosterRepository implements DayRosterRepository {

    private final DayRosters rosters;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param rosters the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which loads a roster's lists
     */
    JpaDayRosterRepository(DayRosters rosters, VersionedSave versionedSave, EntityManager entityManager) {
        this.rosters = Objects.requireNonNull(rosters, "rosters");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<DayRoster> get(DeliveryDate date) {
        Optional<DayRoster> roster = rosters.findByDate(date);
        PersistenceUnitUtil units = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        roster.ifPresent(found -> {
            units.load(found, "entries");
            units.load(found, "unavailableVehicles");
        });
        return roster;
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
