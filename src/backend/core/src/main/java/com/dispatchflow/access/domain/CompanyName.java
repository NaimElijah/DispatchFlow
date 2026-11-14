package com.dispatchflow.access.domain;

import com.dispatchflow.access.domain.AccessError.BlankCompanyName;
import com.dispatchflow.access.domain.AccessError.CompanyNameTooLong;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.util.Objects;

/**
 * The company's name, which starts every message to a recipient (FR-125, FR-382). It is a name
 * under the kernel's one text rule ({@link PlainText}, {@link TextKind#NAME}; ADR-106): NFC, trimmed
 * of spaces, no-break spaces and format characters, and free of control and bidi override
 * characters. On top of that it is not blank, and at most 19 characters, so that with a dispatch
 * phone of up to 11 every message fits one SMS of 70 characters.
 *
 * <p>Its length is counted in UTF-16 units, the units a 70-character SMS is counted in, so a
 * character outside the basic plane counts twice.
 *
 * @param value the name, normalized
 */
public record CompanyName(String value) {

    /** The most characters a company name may have (FR-382). */
    public static final int MAXIMUM_LENGTH = 19;

    /**
     * Normalizes the name and checks it.
     *
     * @throws RuleViolation with {@link SharedKernelError.TextHasForbiddenCharacters} or {@link
     *     SharedKernelError.TextTooLong} when the kernel's text rule refuses it; with {@link
     *     BlankCompanyName} when nothing visible is left; or with {@link CompanyNameTooLong} for
     *     more than 19 characters
     */
    public CompanyName {
        PlainText name = PlainText.of(TextKind.NAME, Objects.requireNonNull(value, "value"));
        if (name.isEmpty()) {
            throw new RuleViolation(new BlankCompanyName());
        }
        value = name.text();
        if (value.length() > MAXIMUM_LENGTH) {
            throw new RuleViolation(new CompanyNameTooLong(MAXIMUM_LENGTH));
        }
    }

    /**
     * Rebuilds a stored name, so a name the rules now refuse is damaged state, not a user's
     * mistake.
     *
     * @param stored the name as stored
     * @return the name
     * @throws StoredStateInvalid when the name's rules refuse the stored text
     */
    public static CompanyName ofStored(String stored) {
        return StoredStateInvalid.whileRestoring(CompanyName.class, () -> new CompanyName(stored));
    }
}
