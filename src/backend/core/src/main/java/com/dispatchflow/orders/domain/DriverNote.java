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
 * A note the driver sees at the stop: a customer's standing notes (FR-08), or an order's own
 * delivery note (FR-135). Blank text is no note, so a note always has text.
 *
 * <p>A note can say anything about a person, so it is personal data: {@link #toString()} hides it,
 * and no error or event carries it.
 */
public final class DriverNote implements PersonalData {

    private final String text;

    private DriverNote(String text) {
        this.text = text;
    }

    /**
     * Reads a note as typed or imported.
     *
     * @param text the note
     * @return the note in the kernel's normal form (ADR-106), or empty when nothing visible is
     *     left
     * @throws RuleViolation with {@link TextTooLong} past the {@link TextKind#NOTE} cap, or with
     *     {@link TextHasForbiddenCharacters}
     */
    public static Optional<DriverNote> of(String text) {
        PlainText plain = PlainText.of(TextKind.NOTE, Objects.requireNonNull(text, "text"));
        return plain.isEmpty() ? Optional.empty() : Optional.of(new DriverNote(plain.text()));
    }

    /**
     * Rebuilds a stored note through the same rule.
     *
     * @param stored the note as stored
     * @return the note
     * @throws StoredStateInvalid when the rule refuses it, or nothing visible is left
     */
    public static DriverNote ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(DriverNote.class, () -> of(stored))
                .orElseThrow(() -> new StoredStateInvalid(DriverNote.class, "a stored note is blank"));
    }

    /**
     * The note's text.
     *
     * @return the text in the kernel's normal form, never empty
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof DriverNote that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the note, because it is personal data.
     *
     * @return {@code DriverNote[redacted]}
     */
    @Override
    public String toString() {
        return "DriverNote[redacted]";
    }
}
