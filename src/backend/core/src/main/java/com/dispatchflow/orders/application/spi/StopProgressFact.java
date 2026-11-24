package com.dispatchflow.orders.application.spi;

import com.dispatchflow.shared.Attempt;
import java.util.Objects;

/**
 * What has happened at an order's stop, as Live Operations answers; Customers &amp; Orders
 * translates it into its {@code StopState} (DL §3.3, BR-92). The effective outcome counts, after
 * any correction (FR-333); a held report is not an outcome (BR-132). Each fact names the attempt
 * whose stop it is (FR-258).
 *
 * <p>To Be Implemented. It is built case by case; at first every stop answers {@link NoFact}
 * (ADR-121).
 */
public sealed interface StopProgressFact {

    /**
     * No arrival and no outcome.
     *
     * <p>To Be Implemented.
     */
    record NoFact() implements StopProgressFact {}

    /**
     * The driver arrived at the attempt's stop, with no outcome yet.
     *
     * <p>To Be Implemented.
     *
     * @param attempt the attempt
     */
    record Arrived(Attempt attempt) implements StopProgressFact {

        /** Checks the attempt is present. */
        public Arrived {
            Objects.requireNonNull(attempt, "attempt");
        }
    }

    /**
     * The attempt's stop was delivered.
     *
     * <p>To Be Implemented.
     *
     * @param attempt the attempt
     */
    record Delivered(Attempt attempt) implements StopProgressFact {

        /** Checks the attempt is present. */
        public Delivered {
            Objects.requireNonNull(attempt, "attempt");
        }
    }

    /**
     * The attempt's stop failed.
     *
     * <p>To Be Implemented.
     *
     * @param attempt the attempt
     */
    record Failed(Attempt attempt) implements StopProgressFact {

        /** Checks the attempt is present. */
        public Failed {
            Objects.requireNonNull(attempt, "attempt");
        }
    }
}
