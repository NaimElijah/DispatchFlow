package com.dispatchflow.fleet.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.application.NotFound;
import java.time.Instant;
import java.util.Optional;

/**
 * The fleet register and the roster, as Planning and Live Operations read them (DL §2.2,
 * ADR-056). Each answer is read now; the caller translates it into its own values (DL §2.3
 * rule 1). A driver's standing combines Access's answer on the account, asked at each read, with
 * the driver record's state (ADR-062, ADR-103).
 *
 * <p>To Be Implemented.
 */
public interface FleetFacts {

    /**
     * The whole fleet register: its depots and vehicles (FR-04, FR-05, FR-129).
     *
     * <p>To Be Implemented.
     *
     * @return the register
     */
    RegisterFacts register();

    /**
     * One date's roster, as a solve and a publication read it (FR-54, FR-194). It keeps only the
     * drivers whose standing is active: a driver on a filled roster may since have retired or lost
     * the Driver role (DL §4.2, ADR-062, ADR-103, FR-193). A date nobody rostered yet has no
     * driver.
     *
     * <p>To Be Implemented.
     *
     * @param date the date
     * @return the roster of that date, its active drivers only
     */
    RosterFacts rosterOn(DeliveryDate date);

    /**
     * One driver's entry on one date's roster, whatever their standing: their shift, their vehicle,
     * whether they are available and their standing, for Live Operations' start deadline and
     * tracking cut-off (FR-101, FR-328, NFR-30). A route published before its driver lost the
     * standing keeps its driver (ADR-062), so this read doesn't drop them.
     *
     * <p>To Be Implemented.
     *
     * @param date the date
     * @param driver the driver's account
     * @return the entry, or empty when the driver is not on that date's roster
     */
    Optional<RosteredDriver> driverOn(DeliveryDate date, AccountId driver);

    /**
     * Whether a driver can take a vehicle on a date, which Planning asks before it hands a route
     * over (FR-302, DL §4.2): {@code DayRoster.checkAssignable}, given the driver's standing. It
     * changes nothing.
     *
     * <p>To Be Implemented.
     *
     * @param date the route's date
     * @param vehicle the route's vehicle
     * @param driver the driver the route would go to
     * @return {@link AssignabilityFact.Assignable} with the driver's shift, or {@link
     *     AssignabilityFact.NotAssignable} with the roster's reason; a date nobody rostered answers
     *     {@link AssignabilityFact.Reason#DRIVER_NOT_ROSTERED} for an active driver
     * @throws NotFound when no account has {@code driver}'s ID
     */
    AssignabilityFact assignability(DeliveryDate date, VehicleId vehicle, AccountId driver);

    /**
     * What changed in the fleet for a date since an instant (BR-25, BR-82, ADR-056).
     *
     * <p>To Be Implemented.
     *
     * @param date the date
     * @param since the instant the changes count from, excluded
     * @return the vehicles and drivers that changed
     */
    FleetChanges changesSince(DeliveryDate date, Instant since);
}
