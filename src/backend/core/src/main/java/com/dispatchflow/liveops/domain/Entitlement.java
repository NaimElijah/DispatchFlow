package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.AccountId;
import java.util.Objects;

/**
 * Planning's answer, through {@code RouteMembership}, on whether a stop was on the report's route in
 * the revision in force at the report's corrected time, and who drove that route then (DL §3.3,
 * BR-02, BR-110). A report is judged by that history, so a late report on a stop that moved since is
 * still accepted (BR-27), and a report from a driver the route was handed away from is accepted only
 * for a time before the hand-over (ADR-114).
 */
public sealed interface Entitlement {

    /**
     * The stop was on the report's route.
     *
     * @param revision the revision in force at the corrected time
     * @param driver the route's driver in that revision; a report from any other account is not
     *     entitled (BR-02, ADR-114)
     */
    record OnRoute(RevisionNo revision, AccountId driver) implements Entitlement {

        /** Checks every part is present. */
        public OnRoute {
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(driver, "driver");
        }
    }

    /** The stop was not on the report's route at that time. */
    record NotOnRoute() implements Entitlement {}
}
