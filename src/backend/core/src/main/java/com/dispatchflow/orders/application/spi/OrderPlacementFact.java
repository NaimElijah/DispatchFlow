package com.dispatchflow.orders.application.spi;

import com.dispatchflow.shared.OrderId;
import java.util.Objects;

/**
 * One order and where its date's plan holds it, in a date's answer (BR-33, DL §9.6).
 *
 * <p>Built in M2.
 *
 * @param order the order
 * @param placement where the plan holds it
 */
public record OrderPlacementFact(OrderId order, PlacementFact placement) {

    /** Checks both parts are present. */
    public OrderPlacementFact {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(placement, "placement");
    }
}
