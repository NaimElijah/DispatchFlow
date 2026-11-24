package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import java.util.Objects;

/**
 * Planning's answer on a stop and a report's route (DL §8, ADR-114): whether the report is
 * entitled, with the route's drivers then and now and the stop's order and attempt then, and where
 * the stop is now. Live Operations builds its {@code Entitlement} and the {@code StopWhereabouts}
 * of its {@code HoldCheck} from it, passes the drivers to {@code RouteRun} for a first stop report,
 * and creates a first fact's {@code Delivery} with the order and attempt of {@link
 * EntitlementFact.OnRoute}. It also stores each alert the report raises with the plan and its date
 * given here.
 *
 * <p>The plan and its date come from the stored revisions, not from the plans in force, so a report
 * on a stop whose plan was since cancelled (FR-336) still raises its alert with both.
 *
 * <p>To Be Implemented.
 *
 * @param plan the plan that holds or held the stop
 * @param date that plan's delivery date
 * @param entitlement whether the stop was on the route at the report's corrected time
 * @param whereabouts where the stop is in the current plan
 */
public record MembershipFact(PlanId plan, DeliveryDate date, EntitlementFact entitlement, WhereaboutsFact whereabouts) {

    /** Checks every part is present. */
    public MembershipFact {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(entitlement, "entitlement");
        Objects.requireNonNull(whereabouts, "whereabouts");
    }
}
