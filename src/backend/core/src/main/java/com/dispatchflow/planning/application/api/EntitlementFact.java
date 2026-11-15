package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.AccountId;
import java.util.Objects;

/**
 * Whether a stop was on a report's route in the revision in force at the report's corrected time,
 * who drove that route then, and who drives it now (BR-02, BR-110, ADR-114). Live Operations
 * translates it into its {@code Entitlement}.
 *
 * <p>Built in M5.
 */
public sealed interface EntitlementFact {

    /**
     * The stop was on the report's route.
     *
     * <p>Built in M5.
     *
     * @param revision the number of the revision in force at the corrected time, 1 or more
     * @param driver the route's driver in that revision
     * @param currentDriver the route's driver in the current revision
     */
    record OnRoute(int revision, AccountId driver, AccountId currentDriver) implements EntitlementFact {

        /** Checks both drivers are present. */
        public OnRoute {
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(currentDriver, "currentDriver");
        }
    }

    /**
     * The stop was not on the report's route at that time, or the route was in no revision then.
     *
     * <p>Built in M5.
     */
    record NotOnRoute() implements EntitlementFact {}
}
