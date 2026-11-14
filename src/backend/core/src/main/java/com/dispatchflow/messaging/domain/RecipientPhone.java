package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * The phone a stop's messages go to (FR-348): the order's own recipient phone when it has one
 * (FR-87), otherwise the customer's contact phone (FR-59), or none.
 */
public sealed interface RecipientPhone {

    /**
     * Chooses the phone by FR-348.
     *
     * @param orderRecipient the order's own recipient phone, if it has one
     * @param customerContact the customer's contact phone, if it still has one
     * @return the recipient's phone first, then the customer's, or {@link None}
     */
    static RecipientPhone choose(Optional<PhoneNumber> orderRecipient, Optional<PhoneNumber> customerContact) {
        Objects.requireNonNull(orderRecipient, "orderRecipient");
        Objects.requireNonNull(customerContact, "customerContact");
        return orderRecipient
                .or(() -> customerContact)
                .<RecipientPhone>map(Known::new)
                .orElseGet(None::new);
    }

    /**
     * Whether a message may go to this phone: only to an Israeli mobile number (FR-378).
     *
     * @return {@code true} for a known Israeli mobile number
     */
    default boolean takesMessages() {
        return this instanceof Known(PhoneNumber phone) && phone.isIsraeliMobile();
    }

    /**
     * A phone to message.
     *
     * @param phone the number
     */
    record Known(PhoneNumber phone) implements RecipientPhone, PersonalData {

        /** Checks the phone is present. */
        public Known {
            Objects.requireNonNull(phone, "phone");
        }

        /**
         * A form that hides the number.
         *
         * @return {@code Known[redacted]}
         */
        @Override
        public String toString() {
            return "Known[redacted]";
        }
    }

    /** Neither the order nor the customer has a phone. */
    record None() implements RecipientPhone {}
}
