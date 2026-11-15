package com.dispatchflow.planning.domain.reconciliation;

import com.dispatchflow.shared.RouteId;
import java.util.Objects;
import java.util.Optional;

/**
 * What reconciling a late fact changed, which the caller uses to raise the dispatcher's alert (DL §8,
 * DL §7.4).
 *
 * @param kind what was done
 * @param from the route the stop left, for {@link ReconciliationKind#MOVED_FROM_ROUTE}
 */
public record Reconciliation(ReconciliationKind kind, Optional<RouteId> from) {

    /** Checks both parts are present, possibly empty. */
    public Reconciliation {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(from, "from");
    }

    /**
     * A reconciliation that names no route.
     *
     * @param kind what was done
     * @return the value
     */
    public static Reconciliation of(ReconciliationKind kind) {
        return new Reconciliation(kind, Optional.empty());
    }

    /**
     * The stop left another driver's route.
     *
     * @param from the route it left
     * @return the value
     */
    public static Reconciliation movedFrom(RouteId from) {
        return new Reconciliation(ReconciliationKind.MOVED_FROM_ROUTE, Optional.of(from));
    }
}
