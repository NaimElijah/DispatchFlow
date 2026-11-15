package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlainText;
import java.util.Objects;
import java.util.Optional;

/**
 * One failed stop or dropped order of a published plan, with what FR-161 groups it by: its reason,
 * its customer and, when it was on a route, its driver. The customer's name is personal data, so
 * it prints redacted; which of these fields the screen may show waits on legal advice (Q-42).
 *
 * <p>Built in an unscheduled milestone: FR-161, a Should, is in no milestone of the roadmap.
 *
 * @param kind whether a stop failed or an order was dropped
 * @param reason the reason's stable code, as its owner names it: a failure reason from Live
 *     Operations (FR-242) or a drop reason from Planning (FR-223)
 * @param date the plan's delivery date
 * @param order the order
 * @param customer the order's customer
 * @param customerName the customer's name, or empty once the customer is anonymized (BR-98)
 * @param driver the driver of the route the stop was on; empty for a dropped order
 */
public record FailureOrDrop(
        Kind kind,
        String reason,
        DeliveryDate date,
        OrderId order,
        CustomerId customer,
        Optional<PlainText> customerName,
        Optional<AccountId> driver)
        implements PersonalData {

    /**
     * Whether a stop failed or an order was dropped.
     *
     * <p>Built in an unscheduled milestone.
     */
    public enum Kind {
        /** A stop on a route failed (FR-242). */
        FAILED,
        /** The plan dropped the order (FR-18, FR-223). */
        DROPPED
    }

    /** Checks every part is present, possibly empty. */
    public FailureOrDrop {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(driver, "driver");
    }

    /**
     * A form that hides the customer's name.
     *
     * @return {@code FailureOrDrop[redacted]}
     */
    @Override
    public String toString() {
        return "FailureOrDrop[redacted]";
    }
}
