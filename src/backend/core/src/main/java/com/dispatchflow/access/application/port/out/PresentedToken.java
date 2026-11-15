package com.dispatchflow.access.application.port.out;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * A refresh token or a credential link's token, as a client presented it (NFR-87, NFR-211). Only
 * its hash is compared with what is stored (NFR-91). It is a secret: it is never logged (NFR-19,
 * NFR-211), so {@link #toString()} prints it redacted.
 *
 * <p>It is a credential, not personal data, but it is marked {@code PersonalData} so the shape
 * rules keep it out of every error and event field (ADR-106).
 *
 * <p>Built in M6.
 *
 * @param value the token as presented
 */
public record PresentedToken(String value) implements PersonalData {

    /** Checks the value is present. */
    public PresentedToken {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A form that hides the token.
     *
     * @return {@code PresentedToken[redacted]}
     */
    @Override
    public String toString() {
        return "PresentedToken[redacted]";
    }
}
