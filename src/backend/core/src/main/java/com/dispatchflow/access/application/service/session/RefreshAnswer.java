package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.IssuedToken;
import com.dispatchflow.access.application.port.out.SignedToken;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * What a refresh answers: new tokens, or the one refusal every refused refresh gets (NFR-92). A
 * refusal is an answer rather than a thrown error, because a reused token revokes the session and
 * that revocation is saved (NFR-88).
 *
 * <p>Built in M6.
 */
public sealed interface RefreshAnswer {

    /**
     * The refresh was granted. Both tokens are secrets, never logged (NFR-19), so it prints
     * redacted.
     *
     * <p>Built in M6.
     *
     * @param accessToken the new access token (NFR-86)
     * @param refreshToken the refresh token that replaces the presented one
     */
    record Refreshed(SignedToken accessToken, IssuedToken refreshToken) implements RefreshAnswer, PersonalData {

        /** Checks every part is present. */
        public Refreshed {
            Objects.requireNonNull(accessToken, "accessToken");
            Objects.requireNonNull(refreshToken, "refreshToken");
        }

        /**
         * A form that hides the tokens.
         *
         * @return {@code RefreshAnswer.Refreshed[redacted]}
         */
        @Override
        public String toString() {
            return "RefreshAnswer.Refreshed[redacted]";
        }
    }

    /**
     * The refresh was refused. Every refusal looks the same (NFR-92).
     *
     * <p>Built in M6.
     */
    record Refused() implements RefreshAnswer {}
}
