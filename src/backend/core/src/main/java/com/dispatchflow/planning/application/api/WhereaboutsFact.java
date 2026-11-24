package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.Objects;

/**
 * Where a stop is in the current plan, compared with the route a report came from (DL §8, BR-89,
 * BR-130, BR-132). Live Operations translates it into its {@code StopWhereabouts}.
 *
 * <p>To Be Implemented.
 */
public sealed interface WhereaboutsFact {

    /**
     * What took a stop out of the plan before a late report reached it (BR-88, BR-130, ADR-107).
     *
     * <p>To Be Implemented.
     */
    enum CancellationKind {
        /** The order was cancelled (FR-207). */
        ORDER_CANCELLED,
        /** The order moved to another date (FR-315). */
        ORDER_RESCHEDULED,
        /** The published plan was cancelled (FR-336). */
        PLAN_CANCELLED
    }

    /**
     * Still on the route the report came from.
     *
     * <p>To Be Implemented.
     */
    record OnServingRoute() implements WhereaboutsFact {}

    /**
     * On another route now (BR-89).
     *
     * <p>To Be Implemented.
     *
     * @param route that route
     */
    record OnAnotherRoute(RouteId route) implements WhereaboutsFact {

        /** Checks the route is present. */
        public OnAnotherRoute {
            Objects.requireNonNull(route, "route");
        }
    }

    /**
     * Dropped by a later re-optimization, or on no route for another reason (DL §8).
     *
     * <p>To Be Implemented.
     */
    record Dropped() implements WhereaboutsFact {}

    /**
     * Gone because its order was cancelled or rescheduled, or its plan cancelled (BR-88, BR-130,
     * ADR-107).
     *
     * <p>To Be Implemented.
     *
     * @param kind what happened
     * @param at when the revision that took the stop off, or the cancellation, took effect
     */
    record Cancelled(CancellationKind kind, Instant at) implements WhereaboutsFact {

        /** Checks both parts are present. */
        public Cancelled {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(at, "at");
        }
    }
}
