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
 * The floor of an order's door (FR-86), as typed: a number, or a word such as a ground floor's
 * name. Blank text is no floor, so a floor always has text. The driver sees it at the stop.
 *
 * <p>It is a class with one factory rather than a record, so a blank one can't be built. It is
 * personal data with the order's address: {@link #toString()} hides it. It takes the kernel's
 * shared text rule, {@code PlainText} with {@code TextKind.NAME} (ADR-106).
 */
public final class Floor implements PersonalData {

    private final String text;

    private Floor(String text) {
        this.text = text;
    }

    /**
     * Reads the floor as typed or imported.
     *
     * @param text the floor
     * @return the floor in the kernel's normal form (ADR-106), or empty when nothing visible is
     *     left
     * @throws RuleViolation with {@link TextTooLong} past the {@link TextKind#NAME} cap, or with
     *     {@link TextHasForbiddenCharacters}
     */
    public static Optional<Floor> of(String text) {
        PlainText plain = PlainText.of(TextKind.NAME, Objects.requireNonNull(text, "text"));
        return plain.isEmpty() ? Optional.empty() : Optional.of(new Floor(plain.text()));
    }

    /**
     * Rebuilds a stored floor through the same rule.
     *
     * @param stored the floor as stored
     * @return the floor
     * @throws StoredStateInvalid when the rule refuses it, or nothing visible is left
     */
    public static Floor ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(Floor.class, () -> of(stored))
                .orElseThrow(() -> new StoredStateInvalid(Floor.class, "a stored floor is blank"));
    }

    /**
     * The floor's text.
     *
     * @return the text in the kernel's normal form, never empty
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Floor that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the text, because it is personal data.
     *
     * @return {@code Floor[redacted]}
     */
    @Override
    public String toString() {
        return "Floor[redacted]";
    }
}
