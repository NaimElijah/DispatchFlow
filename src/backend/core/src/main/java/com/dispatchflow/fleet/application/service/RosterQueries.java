package com.dispatchflow.fleet.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.fleet.application.port.out.FleetViews;
import com.dispatchflow.fleet.application.port.out.RosterView;
import com.dispatchflow.shared.DeliveryDate;

/**
 * The roster screen's query (FR-54, FR-79): one date's shifts, vehicles and availability.
 *
 * <p>Built in M2.
 */
public final class RosterQueries {

    private final FleetViews views;

    /**
     * Creates the query class.
     *
     * @param views the read models Fleet's screens read
     */
    public RosterQueries(FleetViews views) {
        this.views = views;
    }

    /**
     * One date's roster.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param date the date
     * @return the roster, empty for a date nobody rostered
     */
    public RosterView roster(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
