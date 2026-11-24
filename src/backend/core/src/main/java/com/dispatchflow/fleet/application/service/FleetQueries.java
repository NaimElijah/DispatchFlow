package com.dispatchflow.fleet.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.fleet.application.port.out.DriverView;
import com.dispatchflow.fleet.application.port.out.FleetViews;
import com.dispatchflow.fleet.application.port.out.RegisterView;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The register and drivers screens' queries (FR-56, FR-132, FR-193): every depot, vehicle and
 * driver record, active or retired. A retired record is shown so it can be read, and is not offered
 * for new plans (FR-193).
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class FleetQueries {

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
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @return every depot and vehicle
     */
    public RegisterView register(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The driver records.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @return every driver record
     */
    public List<DriverView> drivers(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
