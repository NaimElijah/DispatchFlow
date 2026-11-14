package com.dispatchflow.access.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Where one refresh token of a session's chain stands (DL §4.1; NFR-22, NFR-88, NFR-178,
 * NFR-227).
 */
public sealed interface TokenState {

    /** The token the session's device holds now; exchanging it is the normal refresh. */
    record Current() implements TokenState {}

    /**
     * The token was exchanged once. Presented again within 30 seconds of that exchange, it gets a
     * new token that replaces {@code successor} while the successor is current, and is refused
     * without revoking anything once a step-up superseded the successor (NFR-178). Presented
     * later, or once the successor was itself exchanged, it revokes the session (NFR-88).
     *
     * @param at when it was first exchanged, which the 30 seconds count from
     * @param successor the token its latest exchange issued
     */
    record Exchanged(Instant at, RefreshTokenId successor) implements TokenState {

        /** Checks both parts are present. */
        public Exchanged {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(successor, "successor");
        }
    }

    /**
     * A repeated refresh within the grace replaced the token before it was used. Presenting it
     * revokes the session (NFR-178).
     *
     * @param at when it was replaced
     */
    record Replaced(Instant at) implements TokenState {

        /** Checks the time is present. */
        public Replaced {
            Objects.requireNonNull(at, "at");
        }
    }

    /**
     * Giving the password issued the session new tokens, and this one is refused (NFR-227). It
     * revokes nothing, because it was never exchanged; nor does presenting, within its grace, the
     * token whose successor this one was.
     *
     * @param at when it was superseded
     */
    record Superseded(Instant at) implements TokenState {

        /** Checks the time is present. */
        public Superseded {
            Objects.requireNonNull(at, "at");
        }
    }
}
