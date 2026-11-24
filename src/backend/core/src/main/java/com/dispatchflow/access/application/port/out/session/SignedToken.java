package com.dispatchflow.access.application.port.out.session;

import com.dispatchflow.shared.PersonalData;
import java.time.Instant;
import java.util.Objects;

/**
 * A signed access token, a bearer credential (ADR-045). It is a secret: it is never logged
 * (NFR-19), so {@link #toString()} prints it redacted.
 *
 * <p>To Be Implemented.
 *
 * @param value the compact JWT
 * @param expiresAt when it stops being accepted (NFR-86)
 */
public record SignedToken(String value, Instant expiresAt) implements PersonalData {

    /** Checks every part is present. */
    public SignedToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * A form that hides the token.
     *
     * @return {@code SignedToken[redacted]}
     */
    @Override
    public String toString() {
        return "SignedToken[redacted]";
    }
}
