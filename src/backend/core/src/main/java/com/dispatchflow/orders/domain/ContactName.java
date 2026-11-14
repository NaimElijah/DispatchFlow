package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.BlankContactName;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * The name of a customer (FR-59) or of an order's own recipient (FR-87), trimmed. It is personal
 * data: {@link #toString()} hides it, and no error or event carries it.
 *
 * @param text the name, without surrounding whitespace
 */
public record ContactName(String text) {

    /**
     * Trims the name and checks it has text.
     *
     * @throws RuleViolation with {@link BlankContactName} when the name is empty or only whitespace
     */
    public ContactName {
        text = Objects.requireNonNull(text, "text").strip();
        if (text.isEmpty()) {
            throw new RuleViolation(new BlankContactName());
        }
    }

    /**
     * Creates the name.
     *
     * @param text the name as typed
     * @return the trimmed name
     * @throws RuleViolation with {@link BlankContactName} when the name is empty or only whitespace
     */
    public static ContactName of(String text) {
        return new ContactName(text);
    }

    /**
     * A form that hides the name, because it is personal data.
     *
     * @return {@code ContactName[redacted]}
     */
    @Override
    public String toString() {
        return "ContactName[redacted]";
    }
}
