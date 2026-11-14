package com.dispatchflow.access.domain;

import com.dispatchflow.access.domain.AccessError.PasswordTooCommon;
import com.dispatchflow.access.domain.AccessError.PasswordTooShort;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * A password a user chose, checked against the policy before the hashing adapter turns it into a
 * {@link PasswordHash} (NFR-95, FR-179): at least 8 characters, with no composition rules, and not
 * on the list of the most common passwords.
 *
 * <p>Characters are counted as Unicode code points, so a letter outside the basic plane counts
 * once. {@link #toString()} hides the password, and a refusal never quotes it (NFR-03).
 *
 * <p>It is a credential, not personal data, but it is marked {@link PersonalData} so the shape
 * rules keep it out of every error and event field, as they do personal values (ADR-106).
 */
public final class NewPassword implements PersonalData {

    /** The fewest characters a password may have (NFR-95). */
    public static final int MINIMUM_LENGTH = 8;

    private final String text;

    private NewPassword(String text) {
        this.text = text;
    }

    /**
     * Checks a password against the policy.
     *
     * @param text the password as typed; it is not trimmed, because every character counts
     * @param commonPasswords the bundled list of common passwords
     * @return the password
     * @throws RuleViolation with {@link PasswordTooShort} for fewer than 8 characters, or with
     *     {@link PasswordTooCommon} when the list holds it exactly
     */
    public static NewPassword of(String text, CommonPasswords commonPasswords) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(commonPasswords, "commonPasswords");
        if (text.codePointCount(0, text.length()) < MINIMUM_LENGTH) {
            throw new RuleViolation(new PasswordTooShort(MINIMUM_LENGTH));
        }
        if (commonPasswords.contains(text)) {
            throw new RuleViolation(new PasswordTooCommon());
        }
        return new NewPassword(text);
    }

    /**
     * The password, for the hashing adapter only.
     *
     * @return the password as typed
     */
    public String text() {
        return text;
    }

    /**
     * A form that hides the password.
     *
     * @return {@code NewPassword[redacted]}
     */
    @Override
    public String toString() {
        return "NewPassword[redacted]";
    }
}
