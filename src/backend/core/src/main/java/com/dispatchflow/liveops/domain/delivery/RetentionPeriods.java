package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.shared.IsraelDateTime;
import java.time.Instant;
import java.time.Period;
import java.util.Objects;

/**
 * How long Live Operations keeps its personal data, counted from receipt in Israel time (NFR-31,
 * NFR-184, ADR-075). Live Operations owns its periods (ADR-107). Positions are records, deleted by
 * the nightly job from {@link #positionCutoff(Instant)}; proofs and free-text reasons are erased
 * through {@link Delivery#eraseExpired(Instant)}, which keeps the records.
 */
public final class RetentionPeriods {

    /** How long a vehicle position is kept (NFR-184). */
    public static final Period POSITIONS = Period.ofDays(7);

    /** How long a proof of delivery and its note are kept (NFR-184). */
    public static final Period PROOFS = Period.ofDays(90);

    /**
     * How long the text of an *other* failure reason and of a correction reason is kept: NFR-31
     * gives every kind of personal data a period, and this one follows the proofs it sits beside
     * (ADR-107).
     */
    public static final Period REASONS = Period.ofDays(90);

    private RetentionPeriods() {}

    /**
     * The receipt time at or before which a position is due for deletion.
     *
     * @param now when the retention job runs
     * @return {@code now} less 7 days, in Israel time
     */
    public static Instant positionCutoff(Instant now) {
        return cutoff(now, POSITIONS);
    }

    /**
     * The receipt time at or before which a proof is due for erasure.
     *
     * @param now when the retention job runs
     * @return {@code now} less 90 days, in Israel time
     */
    public static Instant proofCutoff(Instant now) {
        return cutoff(now, PROOFS);
    }

    /**
     * The time at or before which a reason's text is due for erasure.
     *
     * @param now when the retention job runs
     * @return {@code now} less 90 days, in Israel time
     */
    public static Instant reasonCutoff(Instant now) {
        return cutoff(now, REASONS);
    }

    private static Instant cutoff(Instant now, Period period) {
        return Objects.requireNonNull(now, "now")
                .atZone(IsraelDateTime.ZONE)
                .minus(period)
                .toInstant();
    }
}
