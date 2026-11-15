package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * A new refresh token or link token: the secret the client or the email receives, and the hash,
 * which is all that is stored (NFR-91, NFR-211). The secret is never logged (NFR-19, NFR-211), so
 * {@link #toString()} prints it redacted.
 *
 * <p>Built in M5.
 *
 * @param value the token the client or the link carries
 * @param hash its hash, the only part stored
 */
public record IssuedToken(String value, TokenHash hash) implements PersonalData {

    /** Checks every part is present. */
    public IssuedToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(hash, "hash");
    }

    /**
     * A form that hides the token.
     *
     * @return {@code IssuedToken[redacted]}
     */
    @Override
    public String toString() {
        return "IssuedToken[redacted]";
    }
}
