package com.dispatchflow.liveops.domain;

import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StopId;
import java.util.Objects;

/**
 * Whether the order was put back after this stop, and what its new stop has, as the use case reads
 * it before a correction (FR-258, FR-404, ADR-107). Only a failed stop is put back (FR-75).
 */
public sealed interface LaterAttempt {

    /** No put-back since this stop. */
    record None() implements LaterAttempt {}

    /**
     * The order was put back, and its new stop has no outcome; correcting this stop to delivered
     * takes the new stop off (FR-404).
     *
     * @param stop the new stop
     * @param attempt its attempt
     * @param date the delivery date the new stop is on, whose plan holds it (FR-75, FR-258)
     */
    record WithoutOutcome(StopId stop, Attempt attempt, DeliveryDate date) implements LaterAttempt {

        /** Checks every part is present. */
        public WithoutOutcome {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(date, "date");
        }
    }

    /**
     * The order was put back, and its new stop has an outcome, so correcting this stop to delivered
     * is refused (FR-404).
     *
     * @param stop the new stop
     * @param attempt its attempt
     * @param outcome the outcome it has
     */
    record WithOutcome(StopId stop, Attempt attempt, OutcomeKind outcome) implements LaterAttempt {

        /** Checks every part is present. */
        public WithOutcome {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(outcome, "outcome");
        }
    }
}
