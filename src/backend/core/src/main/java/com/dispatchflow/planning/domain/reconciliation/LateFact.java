package com.dispatchflow.planning.domain.reconciliation;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.Objects;

/**
 * An accepted outcome whose stop the current plan holds somewhere else, which Live Operations asks
 * Planning to reconcile (DL §8, BR-89, BR-130).
 *
 * @param stop the stop
 * @param order its order
 * @param attempt its attempt
 * @param servedOn the route that served it: the reporting driver's route
 */
public record LateFact(StopId stop, OrderId order, Attempt attempt, RouteId servedOn) {

    /** Checks every part is present. */
    public LateFact {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(servedOn, "servedOn");
    }
}
