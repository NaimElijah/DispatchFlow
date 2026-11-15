package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * The two phones an order's stop messages may go to, which Messaging's {@code
 * RecipientPhone.choose} picks from in this order (FR-348). Both are personal data, so it prints
 * redacted.
 *
 * <p>Built in M12.
 *
 * @param order the order the phones are for
 * @param orderRecipient the order's own recipient phone, if it has one (FR-87)
 * @param customerContact the customer's contact phone, if it still has one (FR-59)
 */
public record RecipientContactFact(
        OrderId order, Optional<PhoneNumber> orderRecipient, Optional<PhoneNumber> customerContact)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public RecipientContactFact {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(orderRecipient, "orderRecipient");
        Objects.requireNonNull(customerContact, "customerContact");
    }

    /**
     * A form that hides the phones.
     *
     * @return {@code RecipientContactFact[redacted]}
     */
    @Override
    public String toString() {
        return "RecipientContactFact[redacted]";
    }
}
