package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PersonalData;
import java.util.List;
import java.util.Objects;

/**
 * Every order of a date with its status (FR-76, BR-33). Each line holds personal data, so it prints
 * redacted.
 *
 * <p>To Be Implemented.
 *
 * @param date the date
 * @param orders every order of the date, cancelled ones included, each once, ordered by window
 */
public record DateOrdersView(DeliveryDate date, List<OrderLine> orders) implements PersonalData {

    /** Checks the date is present, and copies the orders. */
    public DateOrdersView {
        Objects.requireNonNull(date, "date");
        orders = List.copyOf(orders);
    }

    /**
     * A form that hides the orders.
     *
     * @return {@code DateOrdersView[redacted]}
     */
    @Override
    public String toString() {
        return "DateOrdersView[redacted]";
    }
}
