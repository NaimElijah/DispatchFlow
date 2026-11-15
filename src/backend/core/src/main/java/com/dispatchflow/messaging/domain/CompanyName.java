package com.dispatchflow.messaging.domain;

import com.dispatchflow.messaging.domain.MessagingError.UnusableCompanyName;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.TextKind;
import java.util.Objects;

/**
 * The company's name as every message starts with it (FR-349, FR-382). Access owns the name and
 * checks it (ADR-104); the application layer translates Access's answer into this value (DL
 * §2.3, rule 1), which checks again what a message relies on: a name under the kernel's text rule,
 * not blank, and at most 19 characters, so every message fits 70 (FR-382).
 *
 * <p>The length is counted in UTF-16 units, the units an SMS is counted in, as Access counts it.
 *
 * @param value the name, normalized
 */
public record CompanyName(String value) {

    /** The most characters the name may have (FR-382). */
    public static final int MAXIMUM_LENGTH = 19;

    /**
     * Normalizes the name and checks it.
     *
     * @throws RuleViolation with {@link TextTooLong} or {@link TextHasForbiddenCharacters} when the
     *     kernel's text rule refuses it, or with {@link UnusableCompanyName} when it is blank or
     *     longer than 19 characters
     */
    public CompanyName {
        PlainText name = PlainText.of(TextKind.NAME, Objects.requireNonNull(value, "value"));
        value = name.text();
        if (name.isEmpty() || value.length() > MAXIMUM_LENGTH) {
            throw new RuleViolation(new UnusableCompanyName(MAXIMUM_LENGTH));
        }
    }

    /**
     * Reads a name.
     *
     * @param value the name, from Access
     * @return the name
     * @throws RuleViolation as the constructor does
     */
    public static CompanyName of(String value) {
        return new CompanyName(value);
    }
}
