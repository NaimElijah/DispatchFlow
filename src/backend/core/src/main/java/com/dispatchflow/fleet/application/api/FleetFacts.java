package com.dispatchflow.fleet.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import java.time.Instant;
import java.util.Optional;

/**
 * The fleet register and the roster, as Planning and Live Operations read them (DL §2.2,
 * ADR-056). Each answer is read now; the caller translates it into its own values (DL §2.3
 * rule 1).
 *
 * <p>Built in M2.
 */
public interface FleetFacts {

    /**
     * The whole fleet register: its depots and vehicles (FR-04, FR-05, FR-129).
     *
     * <p>Built in M2.
     *
     * @return the register
     */
    RegisterFacts register();

    /**
     * One date's roster (FR-54, FR-194). A date nobody rostered yet has no driver.
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return the roster of that date
     */
    RosterFacts rosterOn(DeliveryDate date);

    /**
     * One driver's entry on one date's roster: their shift, their vehicle and whether they are
     * available, for Live Operations' start deadline and tracking cut-off (FR-101, FR-328, NFR-30).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @param driver the driver's account
     * @return the entry, or empty when the driver is not on that date's roster
     */
    Optional<RosteredDriver> driverOn(DeliveryDate date, AccountId driver);

    /**
     * What changed in the fleet for a date since an instant (BR-25, BR-82, ADR-056).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @param since the instant the changes count from, excluded
     * @return the vehicles and drivers that changed
     */
    FleetChanges changesSince(DeliveryDate date, Instant since);
}
