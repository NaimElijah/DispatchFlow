package com.dispatchflow.orders.domain;

import java.util.Objects;

/**
 * What has happened at an order's stop, as Live Operations' {@code StopProgress} answers and the
 * application layer translates (DL §3.3, BR-92): nothing, arrived, or an outcome. The effective
 * outcome counts, after any correction (FR-333); a held report is not an outcome (BR-132). Each fact
 * names the attempt whose stop it is, so an earlier attempt's outcome never blocks a later one
 * (FR-258).
 */
public sealed interface StopState {

    /**
     * Whether this is a fact about the given attempt's stop: arrived, delivered or failed (BR-92).
     *
     * @param current the order's current attempt
     * @return {@code true} when the current attempt's stop has a fact
     */
    boolean hasFactFor(Attempt current);

    /** No arrival and no outcome for any attempt. */
    record NoFact() implements StopState {

        @Override
        public boolean hasFactFor(Attempt current) {
            return false;
        }
    }

    /**
     * The driver marked the attempt's stop arrived, with no outcome yet.
     *
     * @param attempt the attempt
     */
    record Arrived(Attempt attempt) implements StopState {

        /** Checks the attempt is given. */
        public Arrived {
            Objects.requireNonNull(attempt, "attempt");
        }

        @Override
        public boolean hasFactFor(Attempt current) {
            return attempt.equals(current);
        }
    }

    /**
     * The attempt's stop was delivered.
     *
     * @param attempt the attempt
     */
    record Delivered(Attempt attempt) implements StopState {

        /** Checks the attempt is given. */
        public Delivered {
            Objects.requireNonNull(attempt, "attempt");
        }

        @Override
        public boolean hasFactFor(Attempt current) {
            return attempt.equals(current);
        }
    }

    /**
     * The attempt's stop failed.
     *
     * @param attempt the attempt
     */
    record Failed(Attempt attempt) implements StopState {

        /** Checks the attempt is given. */
        public Failed {
            Objects.requireNonNull(attempt, "attempt");
        }

        @Override
        public boolean hasFactFor(Attempt current) {
            return attempt.equals(current);
        }
    }
}
