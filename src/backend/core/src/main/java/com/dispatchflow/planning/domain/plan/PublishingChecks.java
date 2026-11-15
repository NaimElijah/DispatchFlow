package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.solution.Evaluation;
import java.util.List;
import java.util.Objects;

/**
 * The facts publishing judges, which the use case reads before its transaction (BR-72, ADR-054, DL
 * §4.4): each route's roster answer, the date's uncancelled orders, and the optimizer's re-check of
 * the plan's current version.
 *
 * @param staffing the roster's answer for each route's vehicle and driver
 * @param orders the date's uncancelled orders
 * @param recheck the optimizer's re-check of the plan as it is
 */
public record PublishingChecks(List<RouteStaffing> staffing, List<DateOrder> orders, Evaluation recheck) {

    /** Copies the lists, and checks the re-check is present. */
    public PublishingChecks {
        staffing = List.copyOf(staffing);
        orders = List.copyOf(orders);
        Objects.requireNonNull(recheck, "recheck");
    }
}
