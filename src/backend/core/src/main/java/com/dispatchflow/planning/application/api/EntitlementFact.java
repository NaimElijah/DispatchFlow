package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import java.util.Objects;

/**
 * Whether a stop was on a report's route in the revision in force at the report's corrected time,
 * who drove that route then, who drives it now, and which order and attempt the stop served in that
 * revision (BR-02, BR-110, ADR-114). Live Operations translates it into its {@code Entitlement}, and
 * takes the order and attempt for a stop's first fact, so a late report on a stop the plan no longer
 * holds still creates its {@code Delivery}.
 *
 * <p>To Be Implemented.
 */
public sealed interface EntitlementFact {

    /**
     * The stop was on the report's route.
     *
     * <p>To Be Implemented.
     *
     * @param revision the number of the revision in force at the corrected time, 1 or more
     * @param driver the route's driver in that revision
     * @param currentDriver the route's driver in the current revision
     * @param order the order the stop served in that revision
     * @param attempt the attempt the stop served in that revision (FR-258)
     */
    record OnRoute(int revision, AccountId driver, AccountId currentDriver, OrderId order, Attempt attempt)
            implements EntitlementFact {

        /** Checks the drivers, the order and the attempt are present. */
        public OnRoute {
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(currentDriver, "currentDriver");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(attempt, "attempt");
        }
    }

    /**
     * The stop was not on the report's route at that time, or the route was in no revision then.
     *
     * <p>To Be Implemented.
     */
    record NotOnRoute() implements EntitlementFact {}
}
