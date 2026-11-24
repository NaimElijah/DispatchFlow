package com.dispatchflow.planning.application.service.plan.edit;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.plan.LayoutToJudge;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read and the transaction of {@link PlaceDroppedOrder}. {@link #layoutAfter} reads, in a
 * read-only transaction, the layout {@code Plan.afterPlacing} gives, for the evaluation. {@link
 * #apply} then loads the plan again, checks the version that read returned, places the dropped
 * order as a new stop as the validator judged, saves the plan and writes the audit record (DL §6.3,
 * TS §6.2, ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class PlaceDroppedOrderChange {

    private final PlanRepository plans;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept
     * @param clock the time, for {@code today}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public PlaceDroppedOrderChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reads the plan as placing the order under the new stop's ID would leave it, for the
     * evaluation before the transaction. The order's attempt is the one the plan drops.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param order the dropped order
     * @param toRoute the route
     * @param position its position there, from 0
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @param newStop the new stop's ID, drawn before this read
     * @return the plan's date, the version read and the layout after the placing
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with what {@code Plan.afterPlacing} refuses
     *     with: Planning's {@code PlanNotReady}, {@code OrderNotDropped}, {@code OrderNotRoutable},
     *     {@code RouteNotInPlan}, {@code PositionOutOfRange}, or {@code RouteLocked}
     */
    @Transactional(readOnly = true)
    public LayoutToJudge layoutAfter(
            Actor actor, PlanId plan, OrderId order, RouteId toRoute, int position, Version expected, StopId newStop) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Places the order under the stop ID the evaluation was asked with, as the evaluation of the
     * plan after the placing judged it.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param order the dropped order
     * @param toRoute the route
     * @param position its position there, from 0
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @param newStop the new stop's ID, drawn before the evaluation
     * @param evaluation the validator's judgement of {@code Plan.afterPlacing}, or the one {@code
     *     FeasibilityChecker.unavailableFor} gave when the validator or the road network couldn't be
     *     reached
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, {@code StaleEvaluation}, {@code ValidatorUnavailable}, {@code
     *     OrderNotDropped}, {@code OrderNotRoutable} for a missing or unreachable location
     *     (FR-237), {@code RouteNotInPlan}, {@code PositionOutOfRange}, or {@code RouteLocked}
     */
    public void apply(
            Actor actor,
            PlanId plan,
            OrderId order,
            RouteId toRoute,
            int position,
            Version expected,
            StopId newStop,
            Evaluation evaluation) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
