package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.IssuedToken;
import com.dispatchflow.access.application.port.out.session.SignedToken;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.SessionId;
import java.util.Objects;

/**
 * What a sign-in or a password step-up gives the device: its session, a new access token and a new
 * refresh token (NFR-86, NFR-87, NFR-227). Both tokens are secrets, never logged (NFR-19), so it
 * prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param session the device's session
 * @param accessToken the access token, accepted for 15 minutes (NFR-86)
 * @param refreshToken the refresh token, which lasts 14 days from its issue (NFR-87)
 */
public record SignedIn(SessionId session, SignedToken accessToken, IssuedToken refreshToken) implements PersonalData {

    /** Checks every part is present. */
    public SignedIn {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
    }

    /**
     * A form that hides the tokens.
     *
     * @return {@code SignedIn[redacted]}
     */
    @Override
    public String toString() {
        return "SignedIn[redacted]";
    }
}
