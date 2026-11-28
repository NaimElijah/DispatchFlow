package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import java.util.Objects;

/**
 * A plan {@link OpenPlan} just opened: its ID, and the version its add stored, which the web
 * adapter answers as the 201's {@code ETag}. So the tag comes from what was saved, never from
 * {@code Version}'s first value (BR-76, ADR-057).
 *
 * @param plan the new plan's ID
 * @param version the version the plan was stored at
 */
public record OpenedPlan(PlanId plan, Version version) {

    /** Checks both parts are present. */
    public OpenedPlan {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(version, "version");
    }
}
