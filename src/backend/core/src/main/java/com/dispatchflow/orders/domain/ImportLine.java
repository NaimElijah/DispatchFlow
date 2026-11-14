package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * An import row whose every field the reader could read (FR-309, ADR-086): the customer it names by
 * address (FR-205), and the order. Its values have checked themselves; the batch adds the rules
 * that need the customers, the dates and the other rows.
 *
 * @param address the {@code address} column
 * @param name the {@code customer_name} column, needed only when no customer matches (FR-311)
 * @param phone the {@code phone} column, needed only when no customer matches (FR-311)
 * @param date the {@code date} column
 * @param order the order's columns
 */
public record ImportLine(
        Address address, Optional<ContactName> name, Optional<PhoneNumber> phone, DeliveryDate date, OrderDetails order)
        implements PersonalData {

    /** Checks every part is given. */
    public ImportLine {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(order, "order");
    }

    /**
     * A form that hides the row, because it holds personal data.
     *
     * @return {@code ImportLine[redacted]}
     */
    @Override
    public String toString() {
        return "ImportLine[redacted]";
    }
}
