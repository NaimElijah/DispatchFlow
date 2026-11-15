package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.planning.application.port.out.ProblemFacts;
import com.dispatchflow.planning.domain.plan.PlanLayout;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import java.util.Objects;

/**
 * What the optimizer's validator is asked to judge: a plan's routes, drops and pins as a change would
 * leave them, for one version of the plan, against the date's facts (FR-23, FR-233, BR-21, DL §6.3,
 * ADR-101 point 15). The layout comes from the plan's {@code layout()} or one of its {@code after…}
 * queries, so the command later leaves exactly the layout judged (ADR-112). The layout's drops
 * with the reason <em>removed by dispatcher</em> are the orders a dispatcher took off (FR-146): the
 * adapter sends them as {@code removed_by_dispatcher}, so the validator gives them that reason.
 *
 * <p>It is the optimizer's daily evaluation: it carries no reported stop, no position and no {@code
 * now}. Its matrix is the plan's stored one, or, when that lacks a location, such as a late order's
 * on an edit check or the publishing re-check, a new one over the new list of locations (ADR-121). Whether evaluating a published plan mid-day takes the daily
 * or the re-optimization rules is open until M9 (see {@link FeasibilityChecker}).
 *
 * <p>Built in M4.
 *
 * @param plan the plan
 * @param planVersion the version the layout was read from, which the {@code Evaluation} names
 * @param layout the routes, drops and pins to judge
 * @param facts the date's orders, fleet, roster, settings, door-window rate, break flag and matrix;
 *     for a hand-over, the route's vehicle is rostered to the layout's new driver (ADR-112)
 */
public record EvaluationRequest(PlanId plan, Version planVersion, PlanLayout layout, ProblemFacts facts) {

    /** Checks every part is present. */
    public EvaluationRequest {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(planVersion, "planVersion");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(facts, "facts");
    }
}
