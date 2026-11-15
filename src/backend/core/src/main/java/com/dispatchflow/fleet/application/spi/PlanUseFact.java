package com.dispatchflow.fleet.application.spi;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import java.util.Objects;

/**
 * One plan that uses a depot, a vehicle or a driver, as Planning answers; Fleet translates it into
 * its own {@code PlanUse} (DL §4.2, FR-305).
 *
 * <p>Built in M2.
 *
 * @param plan the plan
 * @param date the plan's delivery date
 * @param cancelled whether the plan is cancelled
 */
public record PlanUseFact(PlanId plan, DeliveryDate date, boolean cancelled) {

    /** Checks the plan and the date are present. */
    public PlanUseFact {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
    }
}
