package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.RouteId;
import java.util.Objects;
import java.util.Optional;

/**
 * What reconciling a late outcome changed in the plan, which Live Operations raises the
 * dispatcher's alert from (DL §7.4, DL §8). Planning translates its {@code Reconciliation} into
 * it.
 *
 * <p>Built in M8.
 *
 * @param kind what was done
 * @param from the route the stop left, for {@link Kind#MOVED_FROM_ROUTE}; empty otherwise
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

    /** Checks both parts are present, possibly empty. */
    public ReconciliationFact {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(from, "from");
    }
}
