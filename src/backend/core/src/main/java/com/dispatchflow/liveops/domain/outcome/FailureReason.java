package com.dispatchflow.liveops.domain.outcome;

import com.dispatchflow.liveops.domain.LiveOpsError.FailureReasonRequired;
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
 * Why a stop failed: one of BR-109's six fixed reasons, where *other* carries its text (FR-242,
 * FR-73). The text can say anything about a person, so it is personal data: {@link #toString()}
 * hides it, and no error or event carries it. Retention erases the text after {@code
 * RetentionPeriods.REASONS} and keeps the kind, so an erased *other* has no text (NFR-31,
 * ADR-107).
 */
public final class FailureReason implements PersonalData {

    private final FailureReasonKind kind;
    private final @Nullable String text;

    private FailureReason(FailureReasonKind kind, @Nullable String text) {
        this.kind = kind;
        this.text = text;
    }

    /**
     * One of the five reasons that need no text.
     *
     * @param kind the reason
     * @return the reason
     * @throws RuleViolation with {@link FailureReasonRequired} for {@link FailureReasonKind#OTHER},
     *     which needs its text through {@link #other(String)}
     */
    public static FailureReason of(FailureReasonKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (kind == FailureReasonKind.OTHER) {
            throw new RuleViolation(new FailureReasonRequired());
        }
        return new FailureReason(kind, null);
    }

    /**
     * The reason *other*, with the driver's or dispatcher's text (BR-109).
     *
     * @param text the text as typed
     * @return the reason, its text in the kernel's normal form (ADR-106)
     * @throws RuleViolation with {@link FailureReasonRequired} when nothing visible is left, {@link
     *     TextTooLong} past the {@link TextKind#NOTE} cap, or {@link TextHasForbiddenCharacters}
     */
    public static FailureReason other(String text) {
        PlainText plain = PlainText.of(TextKind.NOTE, Objects.requireNonNull(text, "text"));
        if (plain.isEmpty()) {
            throw new RuleViolation(new FailureReasonRequired());
        }
        return new FailureReason(FailureReasonKind.OTHER, plain.text());
    }

    /**
     * Rebuilds a stored reason through the same rules.
     *
     * @param kind the stored reason
     * @param text the stored text, present only for {@link FailureReasonKind#OTHER}, and empty for
     *     one whose text retention erased
     * @return the reason
     * @throws StoredStateInvalid when the rules refuse it, or a fixed reason has a text
     */
    public static FailureReason ofStored(FailureReasonKind kind, Optional<String> text) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        if (kind != FailureReasonKind.OTHER && text.isPresent()) {
            throw new StoredStateInvalid(FailureReason.class, "only the reason other carries a text");
        }
        if (kind == FailureReasonKind.OTHER && text.isEmpty()) {
            return new FailureReason(FailureReasonKind.OTHER, null);
        }
        return StoredStateInvalid.whileRestoring(
                FailureReason.class, () -> text.map(FailureReason::other).orElseGet(() -> of(kind)));
    }

    /**
     * This reason once retention has erased its text (NFR-31); a fixed reason has none to erase.
     *
     * @return the reason with its kind and no text
     */
    public FailureReason erased() {
        return text == null ? this : new FailureReason(kind, null);
    }

    /**
     * Whether retention has erased the text of *other*.
     *
     * @return {@code true} for an *other* with no text left
     */
    public boolean isErased() {
        return kind == FailureReasonKind.OTHER && text == null;
    }

    /**
     * The reason.
     *
     * @return one of BR-109's six
     */
    public FailureReasonKind kind() {
        return kind;
    }

    /**
     * The text of *other*.
     *
     * @return the text, or empty for the five fixed reasons
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof FailureReason that && kind == that.kind && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, text);
    }

    /**
     * A form that names the reason and hides any text, because it is personal data.
     *
     * @return for example {@code FailureReason[OTHER, redacted]}
     */
    @Override
    public String toString() {
        return "FailureReason[" + kind + (text == null ? "" : ", redacted") + "]";
    }
}
