package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.BlankAddress;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.util.Objects;

/**
 * A customer's address as the dispatcher typed it or the import row held it (FR-08, FR-221), in the
 * kernel's normal form: NFC with its ends trimmed, and capped (ADR-106). It is kept and shown so,
 * and matching uses its {@link NormalizedAddress} (FR-310). An address whose normalized form holds
 * no letter or digit, such as {@code -}, {@code "} or a no-break space alone, could never name a
 * place, so it counts as blank.
 *
 * <p>An address is personal data: {@link #toString()} hides it, and no error or event carries it.
 *
 * @param text the address as typed
 */
public record Address(String text) implements PersonalData {

    /**
     * Checks the address has text.
     *
     * @throws RuleViolation with {@link TextTooLong} past the {@link TextKind#ADDRESS} cap, with {@link
     *     TextHasForbiddenCharacters}, or with {@link BlankAddress} when its normalized form holds no
     *     letter or digit
     */
    public Address {
        text = PlainText.of(TextKind.ADDRESS, Objects.requireNonNull(text, "text"))
                .text();
        if (NormalizedAddress.normalize(text).codePoints().noneMatch(Character::isLetterOrDigit)) {
            throw new RuleViolation(new BlankAddress());
        }
    }

    /**
     * Creates the address.
     *
     * @param text the address as typed
     * @return the address
     * @throws RuleViolation with {@link TextTooLong}, {@link TextHasForbiddenCharacters} or {@link
     *     BlankAddress}
     */
    public static Address of(String text) {
        return new Address(text);
    }

    /**
     * Rebuilds a stored address through the same rule.
     *
     * @param stored the address as stored
     * @return the address
     * @throws StoredStateInvalid when the rule refuses it
     */
    public static Address ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(Address.class, () -> new Address(stored));
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
