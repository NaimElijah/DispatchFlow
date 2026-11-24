package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.shared.DeliveryDate;
import java.util.List;

/**
 * The read models of Fleet's screens: the register, the drivers and a date's roster (DL §3.6,
 * CQRS-lite). It never returns an aggregate.
 *
 * <p>To Be Implemented.
 */
public interface FleetViews {

    /**
     * The register, retired records included (FR-56, FR-193).
     *
     * <p>To Be Implemented.
     *
     * @return every depot and vehicle, with none and no version before the register is first
     *     stored
     */
    RegisterView register();

    /**
     * Every driver record, retired and anonymized ones included (FR-56).
     *
     * <p>To Be Implemented.
     *
     * @return the drivers, in the order the screen lists them
     */
    List<DriverView> drivers();

    /**
     * One date's roster (FR-54, FR-79). Each line's standing is the driver record's alone: {@code
     * RETIRED}, {@code ANONYMIZED}, or {@code ACTIVE} for an active record, which {@code
     * RosterQueries} turns into {@code NOT_A_DRIVER} when Access answers that the account is not an
     * active Driver (ADR-062). The role lives in Access, so this port can't read it.
     *
     * <p>To Be Implemented.
     *
     * @param date the date
     * @return the roster, empty and with no version for a date nobody rostered
     */
    RosterView roster(DeliveryDate date);
}
