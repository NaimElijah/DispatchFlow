package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.fleet.domain.roster.DayRoster;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * Where the day rosters are kept, one per delivery date (DL §3.6, §4.2).
 *
 * <p>Built in M2.
 */
public interface DayRosterRepository {

    /**
     * The roster of a date.
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return the roster, or empty when nobody has rostered the date
     */
    Optional<DayRoster> get(DeliveryDate date);

    /**
     * Stores a new roster (DL §3.6).
     *
     * <p>Built in M2.
     *
     * @param roster the new roster, at {@code Version.NEW}
     * @throws StaleVersion when another call stored a roster for the date first
     */
    void add(DayRoster roster);

    /**
     * Stores a changed roster with a version check (DL §3.6, BR-76).
     *
     * <p>Built in M2.
     *
     * @param roster the changed roster
     * @throws StaleVersion when the stored roster is no longer at the version it was loaded at
     */
    void save(DayRoster roster);
}
