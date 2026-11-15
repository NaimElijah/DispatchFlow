package com.dispatchflow.access.application.port.out;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * A password as a person typed it, at sign-in, at a step-up, at a password change or on a link
 * page, before anything checks it (NFR-22, NFR-214). It is a secret: it is never logged, and no
 * error, event or audit record carries it (NFR-19, NFR-03, NFR-94), so {@link #toString()} prints
 * it redacted.
 *
 * <p>It is a credential, not personal data, but it is marked {@code PersonalData} so the shape
 * rules keep it out of every error and event field (ADR-106). It is not checked against the policy:
 * a new password becomes a {@code NewPassword} first (NFR-95).
 *
 * <p>Built in M5.
 *
 * @param value the password as typed, not trimmed
 */
public record PresentedPassword(String value) implements PersonalData {

    /** Checks the value is present. */
    public PresentedPassword {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A form that hides the password.
     *
     * @return {@code PresentedPassword[redacted]}
     */
    @Override
    public String toString() {
        return "PresentedPassword[redacted]";
    }
}
