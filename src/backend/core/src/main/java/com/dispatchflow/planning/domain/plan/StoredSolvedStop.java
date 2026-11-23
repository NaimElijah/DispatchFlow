package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.solution.SolvedStop;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

/**
 * The columns one {@link SolvedStop} is stored in. A solved route holds its stops in a list, which
 * an embeddable can't hold, so every route's stops are flattened into one stop table, each row keyed
 * by its route's position and its own position on that route (ADR-127 decision 3); {@link
 * StoredSolvedRoute#assemble} puts them back.
 */
@Embeddable
class StoredSolvedStop {

    private int routePosition;

    private int stopPosition;

    // PostgreSQL reserves "order".
    @Column(name = "order_id")
    private OrderId order;

    private Attempt attempt;

    @Embedded
    private StoredStopPlan plan;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredSolvedStop() {}

    /**
     * Stores a stop of a route.
     *
     * @param routePosition the route's position among its solution's routes, from 0
     * @param stopPosition the stop's position on its route, from 0
     * @param stop the stop
     */
    StoredSolvedStop(int routePosition, int stopPosition, SolvedStop stop) {
        this.routePosition = routePosition;
        this.stopPosition = stopPosition;
        this.order = stop.order();
        this.attempt = stop.attempt();
        this.plan = new StoredStopPlan(stop.plan());
    }

    /**
     * The position of the route the stop is on.
     *
     * @return the route's position, from 0
     */
    int routePosition() {
        return routePosition;
    }

    /**
     * The stop's position on its route.
     *
     * @return the position, from 0
     */
    int stopPosition() {
        return stopPosition;
    }

    /**
     * The stop these columns store.
     *
     * @return the stop
     * @throws com.dispatchflow.shared.StoredStateInvalid when its plan's stored times or leg are
     *     refused
     */
    SolvedStop solvedStop() {
        return new SolvedStop(order, attempt, plan.stopPlan());
    }
}
