package com.dispatchflow.planning.adapter.out.persistence.plan;

import com.dispatchflow.planning.domain.revision.RevisionRoute;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The columns of one {@link RevisionRoute}, a row of its revision's route table. Its stops are
 * rows of the revision's stop table, keyed by this route's position (ADR-127 decision 3).
 */
@Embeddable
class StoredRevisionRoute {

    @Column(name = "route_id")
    private RouteId route;

    @Column(name = "vehicle_id")
    private VehicleId vehicle;

    @Column(name = "driver_id")
    private AccountId driver;

    /** For Hibernate. */
    protected StoredRevisionRoute() {}

    /**
     * Stores a revision's route, without its stops.
     *
     * @param route the route
     */
    StoredRevisionRoute(RevisionRoute route) {
        this.route = route.route();
        this.vehicle = route.vehicle();
        this.driver = route.driver();
    }
}
