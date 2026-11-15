package com.dispatchflow.access.domain.session;

import com.dispatchflow.access.domain.AccessError.RefreshRefused;
import java.util.Objects;

/**
 * What {@link Session#exchange} answered. A refusal is returned rather than thrown, because a
 * reused token revokes the session, and that revocation must be saved: a thrown refusal rolls the
 * transaction back (NFR-88, NFR-178). Every refusal is the same {@link RefreshRefused}, whether or
 * not it revoked anything (NFR-92).
 */
public sealed interface RefreshOutcome {

    /**
     * The refusal every refused refresh gets.
     *
     * @return a refusal carrying {@link RefreshRefused}
     */
    static RefreshOutcome refused() {
        return new Refused(new RefreshRefused());
    }

    /**
     * The refresh was granted: the device gets this token and a new access token.
     *
     * @param token the new refresh token's ID, whose hash the caller gave
     */
    record Issued(RefreshTokenId token) implements RefreshOutcome {

        /** Checks the token is present. */
        public Issued {
            Objects.requireNonNull(token, "token");
        }
    }

    /**
     * The refresh was refused. The caller saves the session anyway, since the refusal may have
     * revoked it.
     *
     * @param error the one answer every refusal gets
     */
    record Refused(RefreshRefused error) implements RefreshOutcome {

        /** Checks the error is present. */
        public Refused {
            Objects.requireNonNull(error, "error");
        }
    }
}
