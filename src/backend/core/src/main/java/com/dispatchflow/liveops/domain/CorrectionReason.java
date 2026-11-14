package com.dispatchflow.liveops.domain;

import com.dispatchflow.liveops.domain.LiveOpsError.CorrectionReasonRequired;
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
 * Why a dispatcher corrected an outcome; every correction gives one (FR-333). It can say anything
 * about a person, so it is personal data: {@link #toString()} hides it, and no error or event
 * carries it. Retention erases its text after {@link RetentionPeriods#REASONS}, and the correction
 * stays on record without it (NFR-31, ADR-107).
 */
public final class CorrectionReason implements PersonalData {

    private static final CorrectionReason ERASED = new CorrectionReason(null);

    private final @Nullable String text;

    private CorrectionReason(@Nullable String text) {
        this.text = text;
    }

    /**
     * Reads a reason as typed.
     *
     * @param text the reason
     * @return the reason in the kernel's normal form (ADR-106)
     * @throws RuleViolation with {@link TextTooLong} past the {@link TextKind#NOTE} cap, with {@link
     *     TextHasForbiddenCharacters}, or with {@link CorrectionReasonRequired} when nothing visible
     *     is left
     */
    public static CorrectionReason of(String text) {
        PlainText plain = PlainText.of(TextKind.NOTE, Objects.requireNonNull(text, "text"));
        if (plain.isEmpty()) {
            throw new RuleViolation(new CorrectionReasonRequired());
        }
        return new CorrectionReason(plain.text());
    }

    /**
     * Rebuilds a stored reason through the same rule.
     *
     * @param stored the stored text, or empty once retention erased it
     * @return the reason
     * @throws StoredStateInvalid when the rule refuses the text
     */
    public static CorrectionReason ofStored(Optional<String> stored) {
        Objects.requireNonNull(stored, "stored");
        return stored.map(text -> StoredStateInvalid.whileRestoring(CorrectionReason.class, () -> of(text)))
                .orElse(ERASED);
    }

    /**
     * The reason's text.
     *
     * @return the text, or empty once retention erased it
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /**
     * This reason once retention has erased its text (NFR-31).
     *
     * @return the erased reason
     */
    public CorrectionReason erased() {
        return ERASED;
    }

    /**
     * Whether retention has erased the text.
     *
     * @return {@code true} once erased
     */
    public boolean isErased() {
        return text == null;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof CorrectionReason that && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }

    /**
     * A form that hides the reason, because it is personal data.
     *
     * @return {@code CorrectionReason[redacted]}, or {@code CorrectionReason[erased]}
     */
    @Override
    public String toString() {
        return text == null ? "CorrectionReason[erased]" : "CorrectionReason[redacted]";
    }
}
