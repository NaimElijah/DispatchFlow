package com.dispatchflow.planning.application.service.plan.edit;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.plan.LayoutToJudge;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read and the transaction of {@link TakeStopOffRoute}. {@link #layoutAfter} reads, in a
 * read-only transaction, the layout {@code Plan.afterTakeOff} gives, for the evaluation. {@link
 * #apply} then loads the plan again, checks the version that read returned, takes the stop off its
 * route as the validator judged, saves the plan and writes the audit record (DL §6.3, TS §6.2,
 * ADR-063, ADR-129).
 *
 * <p>Built in M4.
 */
@Transactional
public class TakeStopOffRouteChange {

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
    public TakeStopOffRouteChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reads the plan as taking the stop off would leave it, for the evaluation before the
     * transaction.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param stop the stop
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the plan's date, the version read and the layout without the stop
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with what {@code Plan.afterTakeOff} refuses
     *     with: Planning's {@code PlanNotReady}, {@code StopNotInPlan}, or {@code RouteLocked}
     */
    @Transactional(readOnly = true)
    public LayoutToJudge layoutAfter(Actor actor, PlanId plan, StopId stop, Version expected) {
        throw new UnsupportedOperationException("Built in M4");
    }

    /**
     * Takes the stop off, as the evaluation of the plan without it judged it.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param stop the stop
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @param evaluation the validator's judgement of {@code Plan.afterTakeOff}, or the one {@code
     *     FeasibilityChecker.unavailableFor} gave when the validator or the road network couldn't be
     *     reached
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, {@code StaleEvaluation}, {@code ValidatorUnavailable}, {@code
     *     StopNotInPlan}, or {@code RouteLocked}
     */
    public void apply(Actor actor, PlanId plan, StopId stop, Version expected, Evaluation evaluation) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
