package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import java.util.Objects;

/**
 * An order a plan leaves unserved, with its reason (FR-18, FR-144, DL §17 "Drop").
 *
 * @param order the order
 * @param attempt the attempt left unserved
 * @param reason why, with its fact
 */
public record DroppedOrder(OrderId order, Attempt attempt, DropReason reason) {

    /** Checks every part is present. */
    public DroppedOrder {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(reason, "reason");
    }
}
