package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.Objects;

/** Where an alert stands (DL §4.5, §9.7). */
public sealed interface AlertState {

    /** Waiting for a dispatcher. */
    record Open() implements AlertState {}

    /**
     * A dispatcher acknowledged it (FR-260).
     *
     * @param by the dispatcher
     * @param at when
     */
    record Acknowledged(AccountId by, Instant at) implements AlertState {

        /** Checks both parts are present. */
        public Acknowledged {
            Objects.requireNonNull(by, "by");
            Objects.requireNonNull(at, "at");
        }
    }
}
