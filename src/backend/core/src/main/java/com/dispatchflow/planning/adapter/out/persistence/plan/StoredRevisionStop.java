package com.dispatchflow.planning.adapter.out.persistence.plan;

import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.revision.RevisionStop;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;

/**
 * The columns of one {@link RevisionStop}, a row of its revision's stop table, which holds every
 * route's stops keyed by the route's position and the stop's own (ADR-127 decision 3). Its planned
 * arrival is optional, so it is a date and seconds, both or neither.
 */
@Embeddable
class StoredRevisionStop {

    private int routePosition;

    private int stopPosition;

    @Column(name = "stop_id")
    private StopId stop;

    // PostgreSQL reserves "order".
    @Column(name = "order_id")
    private OrderId order;

    private Attempt attempt;

    private @Nullable DeliveryDate plannedArrivalDate;

    private @Nullable Long plannedArrivalSeconds;

    /** For Hibernate. */
    protected StoredRevisionStop() {}

    /**
     * Stores a revision's stop.
     *
     * @param routePosition its route's position in the revision, from 0
     * @param stopPosition its position on that route, from 0
     * @param stop the stop
     */
    StoredRevisionStop(int routePosition, int stopPosition, RevisionStop stop) {
        this.routePosition = routePosition;
        this.stopPosition = stopPosition;
        this.stop = stop.stop();
        this.order = stop.order();
        this.attempt = stop.attempt();
        this.plannedArrivalDate = stop.plannedArrival().map(SolveTime::date).orElse(null);
        this.plannedArrivalSeconds =
                stop.plannedArrival().map(SolveTime::seconds).orElse(null);
    }
}
