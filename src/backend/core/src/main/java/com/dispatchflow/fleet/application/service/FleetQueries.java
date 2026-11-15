package com.dispatchflow.fleet.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.fleet.application.port.out.DriverView;
import com.dispatchflow.fleet.application.port.out.FleetViews;
import com.dispatchflow.fleet.application.port.out.RegisterView;
import java.util.List;

/**
 * The register and drivers screens' queries (FR-56, FR-132, FR-193): every depot, vehicle and
 * driver record, active or retired. A retired record is shown so it can be read, and is not offered
 * for new plans (FR-193).
 *
 * <p>Built in M2.
 */
public final class FleetQueries {

    private final FleetViews views;

    /**
     * Creates the query class.
     *
     * @param views the read models Fleet's screens read
     */
    public FleetQueries(FleetViews views) {
        this.views = views;
    }

    /**
     * The fleet register.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @return every depot and vehicle
     */
    public RegisterView register(Actor actor) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * The driver records.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @return every driver record
     */
    public List<DriverView> drivers(Actor actor) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
