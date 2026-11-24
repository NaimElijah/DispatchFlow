package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.planning.domain.plan.PlanLayout;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.Version;
import java.util.Objects;

/**
 * A plan's layout to send for judging, with its date and the version it was read at. It is what
 * the read-only read of an edit's, a hand-over's, a re-check's or a publication's {@code Change}
 * gives its use case for the evaluation request (ADR-129 decision 3). The write that follows takes
 * {@code planVersion} back, and refuses with {@code StaleVersion} if the plan moved since.
 *
 * @param date the plan's delivery date
 * @param planVersion the version the layout was read from
 * @param layout the plan's routes, drops and pins, as they are or as the change would leave them
 */
public record LayoutToJudge(DeliveryDate date, Version planVersion, PlanLayout layout) {

    /** Checks every part is present. */
    public LayoutToJudge {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(planVersion, "planVersion");
        Objects.requireNonNull(layout, "layout");
    }
}
