package com.dispatchflow.liveops.domain;

import java.util.Objects;

/**
 * Planning's answer, through {@code RouteMembership}, on whether a stop was on the report's route in
 * the revision in force at the report's corrected time (DL §3.3, BR-02, BR-110). A report is judged
 * by that history, so a late report on a stop that moved since is still accepted (BR-27).
 */
public sealed interface Entitlement {

    /**
     * The stop was on the report's route.
     *
     * @param revision the revision in force at the corrected time
     */
    record OnRoute(RevisionNo revision) implements Entitlement {

        /** Checks the revision is present. */
        public OnRoute {
            Objects.requireNonNull(revision, "revision");
        }
    }

    /** The stop was not on the report's route at that time. */
    record NotOnRoute() implements Entitlement {}
}
