package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import java.util.Objects;

/**
 * One plan that uses a depot, a vehicle or a driver (DL §4.2). Planning answers through the {@code
 * RecordUsage} port in Fleet's {@code spi}, with that port's own published types; Fleet's
 * application layer maps the answer to these values before it calls the domain (DL §2.3, rule 1).
 *
 * @param plan the plan
 * @param date the plan's delivery date
 * @param cancelled whether the plan is cancelled
 */
public record PlanUse(PlanId plan, DeliveryDate date, boolean cancelled) {

    /** Checks the plan and date are present. */
    public PlanUse {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
    }

    /**
     * A use by a plan that is not cancelled.
     *
     * @param plan the plan
     * @param date its delivery date
     * @return the use
     */
    public static PlanUse uncancelled(PlanId plan, DeliveryDate date) {
        return new PlanUse(plan, date, false);
    }

    /**
     * A use by a cancelled plan.
     *
     * @param plan the plan
     * @param date its delivery date
     * @return the use
     */
    public static PlanUse cancelled(PlanId plan, DeliveryDate date) {
        return new PlanUse(plan, date, true);
    }
}
