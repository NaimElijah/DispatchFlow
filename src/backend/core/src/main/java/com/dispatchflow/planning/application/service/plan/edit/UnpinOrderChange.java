package com.dispatchflow.planning.application.service.plan.edit;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.plan.LayoutToJudge;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read and the transaction of {@link UnpinOrder}. {@link #layoutAfter} reads, in a read-only
 * transaction, the layout {@code Plan.afterUnpin} gives, for the evaluation. {@link #apply} then
 * loads the plan again, checks the version that read returned, removes the pin as the validator
 * judged, saves the plan and writes the audit record (DL §6.3, TS §6.2, ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class UnpinOrderChange {

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
    public UnpinOrderChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reads the plan as removing the pin would leave it, for the evaluation before the transaction.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param order the pinned order
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the plan's date, the version read and the layout without the pin
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with what {@code Plan.afterUnpin} refuses with:
     *     Planning's {@code PlanNotDraftOrReady} or {@code OrderNotPinned}
     */
    @Transactional(readOnly = true)
    public LayoutToJudge layoutAfter(Actor actor, PlanId plan, OrderId order, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Removes the pin, as the evaluation of the plan without it judged it.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param order the pinned order
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @param evaluation the validator's judgement of {@code Plan.afterUnpin}, or the one {@code
     *     FeasibilityChecker.unavailableFor} gave when the validator or the road network couldn't be
     *     reached
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotDraftOrReady},
     *     {@code DateInPast}, {@code StaleEvaluation}, {@code ValidatorUnavailable}, or {@code
     *     OrderNotPinned}
     */
    public void apply(Actor actor, PlanId plan, OrderId order, Version expected, Evaluation evaluation) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
