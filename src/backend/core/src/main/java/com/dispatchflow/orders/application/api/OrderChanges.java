package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.OrderId;
import java.util.List;

/**
 * What changed among a date's orders since an instant, which Planning's {@code ChangesSinceBasis}
 * holds when a proposal is accepted (BR-25, BR-82, ADR-056).
 *
 * <p>To Be Implemented.
 *
 * @param cancelledOrders the orders of the date cancelled since the instant
 * @param changedOrders the orders of the date changed since the instant, a cancellation excluded
 */
public record OrderChanges(List<OrderId> cancelledOrders, List<OrderId> changedOrders) {

    /** Copies the lists. */
    public OrderChanges {
        cancelledOrders = List.copyOf(cancelledOrders);
        changedOrders = List.copyOf(changedOrders);
    }
}
