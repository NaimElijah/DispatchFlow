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
 * An order's reference in an outside system (FR-85), in the kernel's normal form (ADR-106) and
 * compared exactly. It is unique
 * among a date's uncancelled orders (FR-211, ADR-102). Blank text is no reference, so a reference
 * always has text.
 */
public final class ExternalReference implements PersonalData {

    private final String text;

    private ExternalReference(String text) {
        this.text = text;
    }

    /**
     * Reads a reference as typed or imported.
     *
     * @param text the reference
     * @return the reference in the kernel's normal form (ADR-106), or empty when nothing visible is
     *     left
     * @throws RuleViolation with {@link TextTooLong} past the {@link TextKind#NAME} cap, or with
     *     {@link TextHasForbiddenCharacters}
     */
    public static Optional<ExternalReference> of(String text) {
        PlainText plain = PlainText.of(TextKind.NAME, Objects.requireNonNull(text, "text"));
        return plain.isEmpty() ? Optional.empty() : Optional.of(new ExternalReference(plain.text()));
    }

    /**
     * Rebuilds a stored reference through the same rule.
     *
     * @param stored the reference as stored
     * @return the reference
     * @throws StoredStateInvalid when the rule refuses it, or nothing visible is left
     */
    public static ExternalReference ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(ExternalReference.class, () -> of(stored))
                .orElseThrow(() -> new StoredStateInvalid(ExternalReference.class, "a stored reference is blank"));
    }

    /**
     * The reference's text.
     *
     * @return the text in the kernel's normal form, never empty
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof ExternalReference that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the text: an outside system may build a reference from a customer's data.
     *
     * @return {@code ExternalReference[redacted]}
     */
    @Override
    public String toString() {
        return "ExternalReference[redacted]";
    }
}
