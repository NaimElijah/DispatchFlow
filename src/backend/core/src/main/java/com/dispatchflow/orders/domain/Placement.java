package com.dispatchflow.orders.domain;

import java.util.Objects;

/**
 * Where the plan of an order's date holds it, as Planning's {@code OrderPlacement} answers and the
 * application layer translates (DL §2.2, §9.6). Each answer names the attempt it is about, because a
 * put-back's new attempt is not yet on any plan (FR-258).
 */
public sealed interface Placement {

    /** Whether the plan holding the order still counts for its status (DL §9.6). */
    enum PlanStanding {
        /** A plan that is draft, solving, ready, published or completed. */
        IN_FORCE,
        /** A plan abandoned at the midnight after its date: its orders are unplanned (BR-131). */
        ABANDONED,
        /** A cancelled plan: its orders return to unplanned (FR-337, BR-96). */
        CANCELLED
    }

    /** No plan of the order's date holds it. */
    record NotPlaced() implements Placement {}

    /**
     * A route of a plan serves the attempt.
     *
     * @param attempt the attempt the route serves
     * @param standing the plan's standing
     */
    record Planned(Attempt attempt, PlanStanding standing) implements Placement {

        /** Checks both parts are given. */
        public Planned {
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(standing, "standing");
        }
    }

    /**
     * The plan drops the attempt, with a reason Planning keeps (FR-18).
     *
     * @param attempt the attempt dropped
     * @param standing the plan's standing
     */
    record Dropped(Attempt attempt, PlanStanding standing) implements Placement {

        /** Checks both parts are given. */
        public Dropped {
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(standing, "standing");
        }
    }
}
