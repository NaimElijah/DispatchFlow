package com.dispatchflow.orders.application.spi;

import com.dispatchflow.shared.Attempt;
import java.util.Objects;

/**
 * Where the plan of an order's date holds it, as Planning answers; Customers &amp; Orders
 * translates it into its {@code Placement} (DL §2.2, §9.6). Each answer names the attempt it is
 * about, because a put-back's new attempt is not yet on any plan (FR-258).
 *
 * <p>To Be Implemented.
 */
public sealed interface PlacementFact {

    /**
     * Whether the plan holding the order still counts for its status (DL §9.6), and whether its
     * routes are the drivers' yet. Customers &amp; Orders maps both {@link #UNPUBLISHED} and {@link
     * #PUBLISHED} to its domain's in-force standing, and tells a dispatcher who changes an order on
     * a {@link #PUBLISHED} plan so at once (FR-396).
     *
     * <p>To Be Implemented.
     */
    enum PlanStanding {
        /** A plan in force that is draft, solving or ready. */
        UNPUBLISHED,
        /** A plan in force that is published or completed: its routes are the drivers'. */
        PUBLISHED,
        /** A plan abandoned at the midnight after its date: its orders are unplanned (BR-131). */
        ABANDONED,
        /** A cancelled plan: its orders return to unplanned (FR-337, BR-96). */
        CANCELLED
    }

    /**
     * No plan of the order's date holds it.
     *
     * <p>To Be Implemented.
     */
    record NotPlaced() implements PlacementFact {}

    /**
     * A route of a plan serves the attempt.
     *
     * <p>To Be Implemented.
     *
     * @param attempt the attempt the route serves
     * @param standing the plan's standing
     */
    record Planned(Attempt attempt, PlanStanding standing) implements PlacementFact {

        /** Checks both parts are present. */
        public Planned {
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(standing, "standing");
        }
    }

    /**
     * The plan drops the attempt (FR-18).
     *
     * <p>To Be Implemented.
     *
     * @param attempt the attempt dropped
     * @param standing the plan's standing
     */
    record Dropped(Attempt attempt, PlanStanding standing) implements PlacementFact {

        /** Checks both parts are present. */
        public Dropped {
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(standing, "standing");
        }
    }
}
