package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The plans of one delivery date, so a dispatcher opens the date's plan or creates one (FR-15,
 * BR-34): at most one uncancelled plan, and the cancelled ones before it (FR-336).
 *
 * <p>To Be Implemented.
 *
 * @param date the date
 * @param plans the date's plans, oldest first; empty when it has none
 */
public record DatePlansView(DeliveryDate date, List<Entry> plans) {

    /** Checks the date is present, and copies the plans. */
    public DatePlansView {
        Objects.requireNonNull(date, "date");
        plans = List.copyOf(plans);
    }

    /**
     * One plan of the date.
     *
     * <p>To Be Implemented.
     *
     * @param plan the plan
     * @param version its version
     * @param status its state
     * @param openedAt when it was opened
     */
    public record Entry(PlanId plan, Version version, Plan.Status status, Instant openedAt) {

        /** Checks every part is present. */
        public Entry {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(openedAt, "openedAt");
        }
    }
}
