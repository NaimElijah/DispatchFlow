package com.dispatchflow.fleet.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a driver is in their lifecycle (DL §9.7): active, retired, then anonymized, either a year
 * after retirement (BR-98, NFR-184) or with their account (NFR-258, ADR-103). The record stays in
 * every state, because plans refer to it (BR-43).
 */
public sealed interface DriverStatus {

    /** The driver may be rostered and changed. */
    record Active() implements DriverStatus {}

    /**
     * The driver was retired: offered for no new plan (FR-193), and kept on past ones.
     *
     * @param retiredAt when they were retired
     */
    record Retired(Instant retiredAt) implements DriverStatus {

        /** Checks the time is present. */
        public Retired {
            Objects.requireNonNull(retiredAt, "retiredAt");
        }
    }

    /**
     * The driver's name and phone were removed at the end of retention (BR-98, NFR-258, ADR-075).
     *
     * @param retiredAt when they were retired, or empty for a driver anonymized with their account
     *     while still active
     * @param anonymizedAt when their personal fields were removed
     */
    record Anonymized(Optional<Instant> retiredAt, Instant anonymizedAt) implements DriverStatus {

        /** Checks both are present. */
        public Anonymized {
            Objects.requireNonNull(retiredAt, "retiredAt");
            Objects.requireNonNull(anonymizedAt, "anonymizedAt");
        }
    }
}
