package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import java.time.Instant;
import java.util.Objects;

/**
 * One uncancelled order of the plan's date, as Customers &amp; Orders answered when publishing was
 * requested (BR-22, BR-71, FR-317).
 *
 * @param order the order
 * @param attempt its current attempt
 * @param placedAt when it was placed on the date
 * @param lastChangedAt when it last changed, placing included
 */
public record DateOrder(OrderId order, Attempt attempt, Instant placedAt, Instant lastChangedAt) {

    /** Checks every part is present. */
    public DateOrder {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(placedAt, "placedAt");
        Objects.requireNonNull(lastChangedAt, "lastChangedAt");
    }
}
