package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.BlankAddress;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * A customer's address as the dispatcher typed it or the import row held it (FR-08, FR-221). It is
 * kept and shown exactly as given; matching uses its {@link NormalizedAddress} (FR-310). An address
 * whose normalized form is empty, such as {@code -} or a no-break space alone, could never be
 * matched, so it counts as blank.
 *
 * <p>An address is personal data: {@link #toString()} hides it, and no error or event carries it.
 *
 * @param text the address as typed
 */
public record Address(String text) {

    /**
     * Checks the address has text.
     *
     * @throws RuleViolation with {@link BlankAddress} when the text normalizes to nothing: empty, or
     *     only whitespace and punctuation
     */
    public Address {
        Objects.requireNonNull(text, "text");
        if (NormalizedAddress.normalize(text).isEmpty()) {
            throw new RuleViolation(new BlankAddress());
        }
    }

    /**
     * Creates the address.
     *
     * @param text the address as typed
     * @return the address
     * @throws RuleViolation with {@link BlankAddress} when the text normalizes to nothing
     */
    public static Address of(String text) {
        return new Address(text);
    }

    /**
     * A form that hides the address, because it is personal data.
     *
     * @return {@code Address[redacted]}
     */
    @Override
    public String toString() {
        return "Address[redacted]";
    }
}
