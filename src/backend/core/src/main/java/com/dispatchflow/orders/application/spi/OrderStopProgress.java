package com.dispatchflow.orders.application.spi;

import com.dispatchflow.shared.OrderId;
import java.util.Objects;

/**
 * One order and what has happened at its stop, in a date's answer (BR-33, BR-92, DL §9.6).
 *
 * <p>Built in M5.
 *
 * @param order the order
 * @param progress what has happened at the stop of its latest attempt on the date
 */
public record OrderStopProgress(OrderId order, StopProgressFact progress) {

    /** Checks both parts are present. */
    public OrderStopProgress {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(progress, "progress");
    }
}
