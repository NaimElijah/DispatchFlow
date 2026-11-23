package com.dispatchflow.liveops.domain.proof;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.util.Objects;
import java.util.Optional;

/**
 * The optional note a driver adds to a proof of delivery (FR-30). It can say anything about a
 * person, so it is personal data: {@link #toString()} hides it, and no error or event carries it.
 * Blank text is no note, so a note always has text.
 *
 * @param text the note, in the kernel's normal form
 */
public record ProofNote(String text) implements PersonalData {

    /**
     * Checks the note is in the kernel's normal form and not blank.
     *
     * @throws StoredStateInvalid when it is not, which {@link #of(String)} never builds
     */
    public ProofNote {
        Objects.requireNonNull(text, "text");
        if (!PlainText.ofStored(TextKind.NOTE, text).text().equals(text) || text.isEmpty()) {
            throw new StoredStateInvalid(ProofNote.class, "a proof note is normalized and not blank");
        }
    }

    /**
     * Reads a note as typed.
     *
     * @param text the note
     * @return the note in the kernel's normal form (ADR-106), or empty when nothing visible is left
     * @throws RuleViolation with {@link TextTooLong} past the {@link TextKind#NOTE} cap, or with
     *     {@link TextHasForbiddenCharacters}
     */
    public static Optional<ProofNote> of(String text) {
        PlainText plain = PlainText.of(TextKind.NOTE, Objects.requireNonNull(text, "text"));
        return plain.isEmpty() ? Optional.empty() : Optional.of(new ProofNote(plain.text()));
    }

    /**
     * A form that hides the note, because it is personal data.
     *
     * @return {@code ProofNote[redacted]}
     */
    @Override
    public String toString() {
        return "ProofNote[redacted]";
    }
}
