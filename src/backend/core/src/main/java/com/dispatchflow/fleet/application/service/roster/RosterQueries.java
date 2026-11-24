package com.dispatchflow.fleet.application.service.roster;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.fleet.application.port.out.FleetViews;
import com.dispatchflow.fleet.application.port.out.RosterView;
import com.dispatchflow.shared.DeliveryDate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The roster screen's query (FR-54, FR-79): one date's shifts, vehicles, availability and each
 * driver's standing, so the dispatcher sees why a rostered driver won't be planned (ADR-062,
 * FR-193). It reads the roster from {@link FleetViews}, with each driver record's state, and asks
 * Access's {@link Accounts#standingsOf} once for the date's drivers.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class RosterQueries {

    private final FleetViews views;
    private final Accounts accounts;

    /**
     * Creates the query class.
     *
     * @param views the read models Fleet's screens read
     * @param accounts Access's answer on whether each account is active and holds the Driver role
     */
    public RosterQueries(FleetViews views, Accounts accounts) {
        this.views = views;
        this.accounts = accounts;
    }

    /**
     * One date's roster.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param date the date
     * @return the roster, empty and with no version for a date nobody rostered
     */
    public RosterView roster(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
