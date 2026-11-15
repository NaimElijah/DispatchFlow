package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.OrderStatus;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * One order with its derived status, as its page shows it (FR-57, FR-76, BR-33). It holds personal
 * data, so it prints redacted.
 *
 * <p>Built in M2.
 *
 * @param order everything the order holds
 * @param status its status, derived from the order, Planning's placement and Live Operations'
 *     stop fact (DL §9.6)
 */
public record OrderView(OrderDetail order, OrderStatus status) implements PersonalData {

    /** Checks both parts are present. */
    public OrderView {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(status, "status");
    }

    /**
     * A form that hides the order's personal data.
     *
     * @return {@code OrderView[redacted]}
     */
    @Override
    public String toString() {
        return "OrderView[redacted]";
    }
}
