package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.BlankContactName;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.util.Objects;

/**
 * The name of a customer (FR-59) or of an order's own recipient (FR-87), in the kernel's normal form
 * (ADR-106). It is personal
 * data: {@link #toString()} hides it, and no error or event carries it.
 *
 * @param text the name, without surrounding whitespace
 */
public record ContactName(String text) implements PersonalData {

    /**
     * Puts the name in the kernel's normal form (ADR-106) and checks something visible is left.
     *
     * @throws RuleViolation with {@link TextTooLong} past the {@link TextKind#NAME} cap, with {@link
     *     TextHasForbiddenCharacters}, or with {@link BlankContactName} when nothing visible is left
     */
    public ContactName {
        text = PlainText.of(TextKind.NAME, Objects.requireNonNull(text, "text")).text();
        if (text.isEmpty()) {
            throw new RuleViolation(new BlankContactName());
        }
    }

    /**
     * Creates the name.
     *
     * @param text the name as typed
     * @return the name in the kernel's normal form
     * @throws RuleViolation with {@link TextTooLong}, {@link TextHasForbiddenCharacters} or {@link
     *     BlankContactName}
     */
    public static ContactName of(String text) {
        return new ContactName(text);
    }

    /**
     * Rebuilds a stored name through the same rule.
     *
     * @param stored the name as stored
     * @return the name
     * @throws StoredStateInvalid when the rule refuses it
     */
    public static ContactName ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(ContactName.class, () -> new ContactName(stored));
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
