package com.dispatchflow.liveops.domain.outcome;

import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.Objects;

/** Where a held report stands (DL §9.3, §9.7). */
public sealed interface HeldState {

    /** Waiting for a dispatcher. */
    record Open() implements HeldState {}

    /**
     * A dispatcher confirmed it, and it became the stop's outcome.
     *
     * @param by the dispatcher
     * @param at when
     */
    record Confirmed(AccountId by, Instant at) implements HeldState {

        /** Checks both parts are present. */
        public Confirmed {
            Objects.requireNonNull(by, "by");
            Objects.requireNonNull(at, "at");
        }
    }

    /**
     * A dispatcher refused it, as BR-90 refuses a contradicting report (BR-135).
     *
     * @param by the dispatcher
     * @param at when
     */
    record RefusedByDispatcher(AccountId by, Instant at) implements HeldState {

        /** Checks both parts are present. */
        public RefusedByDispatcher {
            Objects.requireNonNull(by, "by");
            Objects.requireNonNull(at, "at");
        }
    }

    /**
     * Another outcome became the stop's first, so this one was refused as BR-90 says: one on the
     * stop's current route, or another held report confirmed (BR-134, BR-18).
     *
     * @param at when
     */
    record Overtaken(Instant at) implements HeldState {

        /** Checks the time is present. */
        public Overtaken {
            Objects.requireNonNull(at, "at");
        }
    }
}
