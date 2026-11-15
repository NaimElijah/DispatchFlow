package com.dispatchflow.fleet.domain.driver;

import com.dispatchflow.fleet.domain.FleetError.BlankDriverName;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.util.Objects;

/**
 * A driver's name (DL §4.2). It is personal data (NFR-19, ADR-106), so {@link #toString()} hides it
 * and no error or event carries it; it is removed when the driver is anonymized (BR-98).
 *
 * <p>It is read through the kernel's text rule as a {@link TextKind#NAME} (ADR-106): Unicode NFC,
 * trimmed of whitespace, no-break spaces and format characters at both ends, at most 100 code
 * points, and without control or bidi characters. Two names are the same when their normalized
 * text is equal, which is case-sensitive.
 *
 * @param value the normalized name
 */
public record DriverName(String value) implements PersonalData {

    /**
     * Normalizes and checks the name.
     *
     * @throws RuleViolation with {@link BlankDriverName} when nothing is left after normalizing, or with
     *     the kernel's {@link TextTooLong} or {@link TextHasForbiddenCharacters}
     */
    public DriverName {
        PlainText text = PlainText.of(TextKind.NAME, Objects.requireNonNull(value, "value"));
        if (text.isEmpty()) {
            throw new RuleViolation(new BlankDriverName());
        }
        value = text.text();
    }

    /**
     * Reads a name as entered or imported.
     *
     * @param text the name as typed
     * @return the normalized name
     * @throws RuleViolation with {@link BlankDriverName} when nothing is left after normalizing, or with
     *     the kernel's {@link TextTooLong} or {@link TextHasForbiddenCharacters}
     */
    public static DriverName of(String text) {
        return new DriverName(text);
    }

    /**
     * Rebuilds a stored name through the same rule, so a name the rule refuses is damaged state,
     * never a user's mistake.
     *
     * @param stored the name as stored
     * @return the name
     * @throws StoredStateInvalid when the rule refuses the stored name, or it is blank
     */
    public static DriverName ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(DriverName.class, () -> new DriverName(stored));
    }

    /**
     * A form that hides the name, because it is personal data.
     *
     * @return {@code DriverName[redacted]}
     */
    @Override
    public String toString() {
        return "DriverName[redacted]";
    }
}
