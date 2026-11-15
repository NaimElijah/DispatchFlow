package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.shared.AccountId;
import java.util.Objects;

/**
 * Planning's answer, through {@code RouteMembership}, on whether a stop was on the report's route in
 * the revision in force at the report's corrected time, who drove that route then, and who drives it
 * now (DL §3.3, BR-02, BR-110). A report is judged by that history, so a late report on a stop that
 * moved since is still accepted (BR-27). A report from a driver the route was handed away from is
 * refused for a time after the hand-over, and for a time before it is never recorded directly: an
 * outcome is held for the dispatcher and an arrival is refused (ADR-114).
 */
public sealed interface Entitlement {

    /**
     * The stop was on the report's route.
     *
     * @param revision the revision in force at the corrected time
     * @param driver the route's driver in that revision; a report from any other account is not
     *     entitled (BR-02, ADR-114)
     * @param currentDriver the route's driver in the current revision; when it is not {@code
     *     driver}, the route was handed over since (ADR-114)
     */
    record OnRoute(RevisionNo revision, AccountId driver, AccountId currentDriver) implements Entitlement {

        /** Checks every part is present. */
        public OnRoute {
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(currentDriver, "currentDriver");
        }

        /**
         * Whether the route was handed to another driver after the report's time.
         *
         * @return {@code true} when the route's driver now is not its driver then
         */
        public boolean isHandedOverSince() {
            return !driver.equals(currentDriver);
        }
    }

    /** The stop was not on the report's route at that time. */
    record NotOnRoute() implements Entitlement {}
}
