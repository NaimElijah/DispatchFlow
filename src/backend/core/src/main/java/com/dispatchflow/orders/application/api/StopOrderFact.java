package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlainText;
import java.util.Objects;
import java.util.Optional;

/**
 * What a driver sees at one order's stop, and the door flag Live Operations judges a report by
 * (FR-27, FR-86, FR-87, FR-135, FR-364, FR-366): Live Operations makes its {@code LeaveAtDoor}
 * from {@code leaveAtDoor}, and its driver's day from the rest. Every text and phone is a person's
 * data, so it prints redacted. A field anonymization removed is empty (NFR-257, BR-98).
 *
 * <p>To Be Implemented.
 *
 * @param order the order the fields are for
 * @param leaveAtDoor whether the order may be left at the door (FR-364)
 * @param address the customer's address as typed, or empty once anonymized
 * @param floor the order's floor, if given (FR-86)
 * @param apartment the order's apartment, if given (FR-86)
 * @param entranceCode the order's entrance code, if given (FR-86)
 * @param orderNote the order's own delivery note, if any (FR-135)
 * @param customerNote the customer's standing notes for drivers, if any (FR-08, FR-135)
 * @param recipientName the order's own recipient name, if given (FR-87)
 * @param recipientPhone the order's own recipient phone, if given (FR-87)
 * @param customerName the customer's contact name, which the driver sees beside the recipient
 *     (FR-59, FR-87)
 * @param customerPhone the customer's contact phone (FR-59, FR-87)
 */
public record StopOrderFact(
        OrderId order,
        boolean leaveAtDoor,
        Optional<PlainText> address,
        Optional<PlainText> floor,
        Optional<PlainText> apartment,
        Optional<PlainText> entranceCode,
        Optional<PlainText> orderNote,
        Optional<PlainText> customerNote,
        Optional<PlainText> recipientName,
        Optional<PhoneNumber> recipientPhone,
        Optional<PlainText> customerName,
        Optional<PhoneNumber> customerPhone)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public StopOrderFact {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(apartment, "apartment");
        Objects.requireNonNull(entranceCode, "entranceCode");
        Objects.requireNonNull(orderNote, "orderNote");
        Objects.requireNonNull(customerNote, "customerNote");
        Objects.requireNonNull(recipientName, "recipientName");
        Objects.requireNonNull(recipientPhone, "recipientPhone");
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(customerPhone, "customerPhone");
    }

    /**
     * A form that hides the person's data.
     *
     * @return {@code StopOrderFact[redacted]}
     */
    @Override
    public String toString() {
        return "StopOrderFact[redacted]";
    }
}
