package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The code that opens an order's building (FR-86). Blank text is no entrance code, so an entrance
 * code always has text. The driver sees it at the stop.
 *
 * <p>It is a class with one factory rather than a record, so a blank one can't be built. It is
 * personal data with the order's address: {@link #toString()} hides it. It takes the kernel's
 * shared text rule, {@code PlainText} with {@code TextKind.NAME} (ADR-106).
 */
public final class EntranceCode implements PersonalData {

    private final String text;

    private EntranceCode(String text) {
        this.text = text;
    }

    /**
     * Reads the entrance code as typed or imported.
     *
     * @param text the entrance code
     * @return the entrance code in the kernel's normal form (ADR-106), or empty when nothing visible is
     *     left
     * @throws RuleViolation with {@link TextTooLong} past the {@link TextKind#NAME} cap, or with
     *     {@link TextHasForbiddenCharacters}
     */
    public static Optional<EntranceCode> of(String text) {
        PlainText plain = PlainText.of(TextKind.NAME, Objects.requireNonNull(text, "text"));
        return plain.isEmpty() ? Optional.empty() : Optional.of(new EntranceCode(plain.text()));
    }

    /**
     * Rebuilds a stored entrance code through the same rule.
     *
     * @param stored the entrance code as stored
     * @return the entrance code
     * @throws StoredStateInvalid when the rule refuses it, or nothing visible is left
     */
    public static EntranceCode ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(EntranceCode.class, () -> of(stored))
                .orElseThrow(() -> new StoredStateInvalid(EntranceCode.class, "a stored entrance code is blank"));
    }

    /**
     * The entrance code's text.
     *
     * @return the text in the kernel's normal form, never empty
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof EntranceCode that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the text, because it is personal data.
     *
     * @return {@code EntranceCode[redacted]}
     */
    @Override
    public String toString() {
        return "EntranceCode[redacted]";
    }
}
