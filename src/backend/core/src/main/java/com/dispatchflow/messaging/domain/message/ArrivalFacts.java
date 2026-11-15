package com.dispatchflow.messaging.domain.message;

import com.dispatchflow.messaging.domain.ProjectedArrival;
import java.util.Objects;

/**
 * What Messaging's minute job knows about a stop when it asks for its projected arrival (FR-352,
 * FR-353, DL §10.3).
 *
 * @param route how far the stop's route has come
 * @param previousStop the stop before it in plan order
 * @param stop how far the stop itself has come
 * @param arrival the stop's projected arrival
 */
public record ArrivalFacts(
        RouteProgress route, PreviousStop previousStop, StopProgress stop, ProjectedArrival arrival) {

    /** The stop before this one in plan order (FR-352). */
    public enum PreviousStop {
        /** This stop is the route's first. */
        NONE,
        /** The stop before has an outcome. */
        HAS_OUTCOME,
        /** The stop before has no outcome yet. */
        NO_OUTCOME
    }

    /** Checks every part is present. */
    public ArrivalFacts {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(previousStop, "previousStop");
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(arrival, "arrival");
    }
}
