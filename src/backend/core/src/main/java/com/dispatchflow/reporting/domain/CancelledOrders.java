package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.OrderId;
import java.util.List;
import java.util.Objects;

/**
 * The orders of a date that are cancelled now, as Customers &amp; Orders' view gives them. Neither
 * on-time rate counts them, and neither does the baseline's (BR-30, ADR-109).
 *
 * @param orders the cancelled orders, in the order the view gave them
 */
public record CancelledOrders(List<OrderId> orders) {

    /** Copies the orders. */
    public CancelledOrders {
        orders = List.copyOf(orders);
    }

    /**
     * No order is cancelled.
     *
     * @return the empty set
     */
    public static CancelledOrders none() {
        return new CancelledOrders(List.of());
    }

    /**
     * Creates the set.
     *
     * @param orders the cancelled orders
     * @return the set
     */
    public static CancelledOrders of(List<OrderId> orders) {
        return new CancelledOrders(orders);
    }

    /**
     * Whether an order is cancelled.
     *
     * @param order the order
     * @return {@code true} when it is among the cancelled
     */
    public boolean contains(OrderId order) {
        return orders.contains(Objects.requireNonNull(order, "order"));
    }
}
