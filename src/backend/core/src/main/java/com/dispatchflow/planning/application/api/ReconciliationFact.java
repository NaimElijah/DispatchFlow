package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.RouteId;
import java.util.Objects;
import java.util.Optional;

/**
 * What reconciling a late outcome changed in the plan (DL §7.4, DL §8). Planning translates its
 * {@code Reconciliation} into it. Live Operations has raised the dispatcher's alert before it asks,
 * from where the stop was (ADR-121), so the answer reports the change and names no alert.
 *
 * <p>Built in M8.
 *
 * @param kind what was done
 * @param from the route the stop left, present exactly for {@link Kind#MOVED_FROM_ROUTE}
 */
public record ReconciliationFact(Kind kind, Optional<RouteId> from) {

    /**
     * What reconciling did to the plan (DL §8's table, ADR-101).
     *
     * <p>Built in M8.
     */
    public enum Kind {
        /** The stop was already on the route that served it. */
        UNCHANGED,
        /** The stop left another driver's route for the serving one (BR-89). */
        MOVED_FROM_ROUTE,
        /** The stop left the drops for the serving route. */
        FROM_DROPPED,
        /** The stop's order was cancelled; it joined the serving route as served after cancellation (BR-130). */
        AFTER_CANCELLATION,
        /** The plan is cancelled and stays as it is; Live Operations records the fact (ADR-101). */
        PLAN_CANCELLED
    }

    /**
     * Checks both parts are present, and that {@code from} is present exactly for a moved stop.
     *
     * @throws IllegalArgumentException when {@code from} is present for another kind, or empty for
     *     {@link Kind#MOVED_FROM_ROUTE}
     */
    public ReconciliationFact {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(from, "from");
        // Only a move leaves a route, so only a move may name one, and a move must.
        if (from.isPresent() != (kind == Kind.MOVED_FROM_ROUTE)) {
            throw new IllegalArgumentException("from is present exactly for MOVED_FROM_ROUTE");
        }
    }
}
