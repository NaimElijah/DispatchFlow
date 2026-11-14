package com.dispatchflow.fleet.domain;

import com.dispatchflow.fleet.domain.FleetError.BlankDepotName;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.util.Objects;

/**
 * A depot's name, as dispatchers see it (FR-04, FR-56).
 *
 * <p>It is read through the kernel's text rule as a {@link TextKind#NAME} (ADR-106): Unicode NFC,
 * trimmed of whitespace, no-break spaces and format characters at both ends, at most 100 code
 * points, and without control or bidi characters. Two names are the same when their normalized
 * text is equal, which is case-sensitive.
 *
 * @param value the normalized name
 */
public record DepotName(String value) {

    /**
     * Normalizes and checks the name.
     *
     * @throws RuleViolation with {@link BlankDepotName} when nothing is left after normalizing, or with
     *     the kernel's {@link TextTooLong} or {@link TextHasForbiddenCharacters}
     */
    public DepotName {
        PlainText text = PlainText.of(TextKind.NAME, Objects.requireNonNull(value, "value"));
        if (text.isEmpty()) {
            throw new RuleViolation(new BlankDepotName());
        }
        value = text.text();
    }

    /**
     * Reads a name as entered or imported.
     *
     * @param text the name as typed
     * @return the normalized name
     * @throws RuleViolation with {@link BlankDepotName} when nothing is left after normalizing, or with
     *     the kernel's {@link TextTooLong} or {@link TextHasForbiddenCharacters}
     */
    public static DepotName of(String text) {
        return new DepotName(text);
    }

    /**
     * Rebuilds a stored name through the same rule, so a name the rule refuses is damaged state,
     * never a user's mistake.
     *
     * @param stored the name as stored
     * @return the name
     * @throws StoredStateInvalid when the rule refuses the stored name, or it is blank
     */
    public static DepotName ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(DepotName.class, () -> new DepotName(stored));
    }
}
