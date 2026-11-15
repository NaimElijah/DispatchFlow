package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.shared.PlanId;
import java.util.Objects;
import java.util.Optional;

/**
 * The date's uncancelled plan, if it has one, as the use case read it before opening a plan (BR-34,
 * ADR-101). A partial unique index on the date backs it, so a race the read misses still fails.
 *
 * @param uncancelled the date's uncancelled plan, or empty
 */
public record PlansOnDate(Optional<PlanId> uncancelled) {

    /** Checks the plan is present, possibly empty. */
    public PlansOnDate {
        Objects.requireNonNull(uncancelled, "uncancelled");
    }

    /**
     * A date with no uncancelled plan.
     *
     * @return the value
     */
    public static PlansOnDate none() {
        return new PlansOnDate(Optional.empty());
    }

    /**
     * A date that already has an uncancelled plan.
     *
     * @param plan that plan
     * @return the value
     */
    public static PlansOnDate of(PlanId plan) {
        return new PlansOnDate(Optional.of(plan));
    }
}
