package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.shared.DeliveryDate;
import java.util.List;

/**
 * The read models of Fleet's screens: the register, the drivers and a date's roster (DL §3.6,
 * CQRS-lite). It never returns an aggregate.
 *
 * <p>Built in M2.
 */
public interface FleetViews {

    /**
     * The register, retired records included (FR-56, FR-193).
     *
     * <p>Built in M2.
     *
     * @return every depot and vehicle
     */
    RegisterView register();

    /**
     * Every driver record, retired and anonymized ones included (FR-56).
     *
     * <p>Built in M2.
     *
     * @return the drivers, in the order the screen lists them
     */
    List<DriverView> drivers();

    /**
     * One date's roster (FR-54, FR-79).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return the roster, empty for a date nobody rostered
     */
    RosterView roster(DeliveryDate date);
}
