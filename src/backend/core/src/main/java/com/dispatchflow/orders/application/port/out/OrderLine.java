package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.OrderStatus;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * One order of a date's list with its derived status (FR-76, BR-33, DL §9.6). It holds the order's
 * row, which is personal data, so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param order the order as the list shows it
 * @param status its status, derived from the order, Planning's placement and Live Operations'
 *     stop fact; a cancellation still shows beside an outcome through {@link OrderRow#cancelled()}
 */
public record OrderLine(OrderRow order, OrderStatus status) implements PersonalData {

    /** Checks both parts are present. */
    public OrderLine {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(status, "status");
    }

    /**
     * A form that hides the order's personal data.
     *
     * @return {@code OrderLine[redacted]}
     */
    @Override
    public String toString() {
        return "OrderLine[redacted]";
    }
}
