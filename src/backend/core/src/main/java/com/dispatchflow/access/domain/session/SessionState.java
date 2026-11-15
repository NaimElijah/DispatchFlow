package com.dispatchflow.access.domain.session;

import java.time.Instant;
import java.util.Objects;

/** Where a session stands (DL §4.1, §9.7): live until it is revoked, and then for good. */
public sealed interface SessionState {

    /** The session may refresh and call (NFR-22). */
    record Live() implements SessionState {}

    /**
     * The session ended: signed out (NFR-89), or a reused refresh token revoked it (NFR-88,
     * NFR-178).
     *
     * @param at when
     */
    record Revoked(Instant at) implements SessionState {

        /** Checks the time is present. */
        public Revoked {
            Objects.requireNonNull(at, "at");
        }
    }
}
