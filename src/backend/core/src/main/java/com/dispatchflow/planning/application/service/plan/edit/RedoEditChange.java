package com.dispatchflow.planning.application.service.plan.edit;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.domain.plan.PlanLayout;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read and the transaction of {@link RedoEdit}. {@link #layoutAfter} reads, in a read-only
 * transaction, the layout {@code Plan.afterRedo} gives, for the evaluation. {@link #apply} then
 * loads the plan again, checks the version that read returned, redoes the last undone edit as the
 * validator judged, saves the plan and writes the audit record (DL §6.3, TS §6.2, ADR-063,
 * ADR-129).
 *
 * <p>Built in M4.
 */
@Transactional
public class RedoEditChange {

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
    public RedoEditChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reads the plan as the redo would leave it, for the evaluation before the transaction.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the plan's date, the version read and the layout after the redo
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with what {@code Plan.afterRedo} refuses with:
     *     Planning's {@code PlanNotReady} or {@code NothingToRedo}
     */
    @Transactional(readOnly = true)
    public LayoutToJudge layoutAfter(Actor actor, PlanId plan, Version expected) {
        throw new UnsupportedOperationException("Built in M4");
    }

    /**
     * Redoes the edit, as the evaluation of the plan after the redo judged it.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @param evaluation the validator's judgement of {@code Plan.afterRedo}, or the one {@code
     *     FeasibilityChecker.unavailableFor} gave when the validator or the road network couldn't be
     *     reached
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, {@code NothingToRedo}, {@code StaleEvaluation}, or {@code
     *     ValidatorUnavailable}
     */
    public void apply(Actor actor, PlanId plan, Version expected, Evaluation evaluation) {
        throw new UnsupportedOperationException("Built in M4");
    }

    /**
     * The plan as {@code Plan.afterRedo} gives it, with its date and the version it was read at,
     * for the evaluation request.
     *
     * @param date the plan's delivery date
     * @param planVersion the version the layout was read from
     * @param layout the plan's routes, drops and pins as the change would leave them
     */
    public record LayoutToJudge(DeliveryDate date, Version planVersion, PlanLayout layout) {

        /** Checks every part is present. */
        public LayoutToJudge {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(planVersion, "planVersion");
            Objects.requireNonNull(layout, "layout");
        }
    }
}
