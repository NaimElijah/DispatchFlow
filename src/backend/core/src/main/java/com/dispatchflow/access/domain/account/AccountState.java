package com.dispatchflow.access.domain.account;

import java.time.Instant;
import java.util.Objects;

/**
 * Where an account stands (DL §4.1, §9.7). A deactivated account can't sign in and keeps its
 * history; a year later it is anonymized, and its record stays because plans and audit records
 * refer to it (FR-53, FR-297, NFR-258, NFR-260).
 */
public sealed interface AccountState {

    /** The account may sign in, if it holds a role (BR-123). */
    record Active() implements AccountState {}

    /**
     * The account was deactivated (FR-53).
     *
     * @param at when
     */
    record Deactivated(Instant at) implements AccountState {

        /** Checks the time is present. */
        public Deactivated {
            Objects.requireNonNull(at, "at");
        }
    }

    /**
     * The account's personal data was removed a year after its deactivation (NFR-258, NFR-260).
     *
     * @param deactivatedAt when it was deactivated
     * @param anonymizedAt when it was anonymized
     */
    record Anonymized(Instant deactivatedAt, Instant anonymizedAt) implements AccountState {

        /** Checks both times are present. */
        public Anonymized {
            Objects.requireNonNull(deactivatedAt, "deactivatedAt");
            Objects.requireNonNull(anonymizedAt, "anonymizedAt");
        }
    }
}
