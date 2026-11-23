package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import java.time.Instant;
import java.util.Objects;

/**
 * One outcome on record: the original or a correction, which never replaces it (FR-333).
 *
 * @param outcome what it says
 * @param by the driver who reported it, or the dispatcher who recorded or corrected it
 * @param factTime the time the outcome counts from: the report's corrected time, the time a
 *     dispatcher recorded it, or for a correction the time it states or else the original's
 *     (ADR-107)
 * @param origin how it was recorded
 */
public record OutcomeRecord(Outcome outcome, AccountId by, Instant factTime, Origin origin) implements PersonalData {

    /** Checks every part is present. */
    public OutcomeRecord {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(factTime, "factTime");
        Objects.requireNonNull(origin, "origin");
    }

    /**
     * A form that leaves out the outcome's and the origin's personal content.
     *
     * @return the record, redacted
     */
    @Override
    public String toString() {
        return "OutcomeRecord[outcome=" + outcome + ", by=" + by + ", factTime=" + factTime + ", origin=" + origin
                + "]";
    }
}
