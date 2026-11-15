package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.solution.PinTarget;
import com.dispatchflow.shared.OrderId;
import java.util.Objects;

/**
 * An order fixed to a vehicle or a driver, which the next solve honours or drops the order for
 * (FR-91, FR-228). A plan holds at most one pin per order.
 *
 * @param order the order
 * @param target what it is pinned to
 */
public record Pin(OrderId order, PinTarget target) {

    /** Checks both parts are present. */
    public Pin {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(target, "target");
    }
}
