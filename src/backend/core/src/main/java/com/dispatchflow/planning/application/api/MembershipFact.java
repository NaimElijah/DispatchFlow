package com.dispatchflow.planning.application.api;

import java.util.Objects;

/**
 * Planning's answer on a stop and a report's route (DL §8, ADR-114): whether the report is
 * entitled, with the route's drivers then and now and the stop's order and attempt then, and where
 * the stop is now. Live Operations builds its {@code Entitlement} and the {@code StopWhereabouts} of
 * its {@code HoldCheck} from it, passes the drivers to {@code RouteRun} for a first stop report, and
 * creates a first fact's {@code Delivery} with the order and attempt of {@link
 * EntitlementFact.OnRoute}.
 *
 * <p>Built in M5.
 *
 * @param entitlement whether the stop was on the route at the report's corrected time
 * @param whereabouts where the stop is in the current plan
 */
public record MembershipFact(EntitlementFact entitlement, WhereaboutsFact whereabouts) {

    /** Checks both parts are present. */
    public MembershipFact {
        Objects.requireNonNull(entitlement, "entitlement");
        Objects.requireNonNull(whereabouts, "whereabouts");
    }
}
