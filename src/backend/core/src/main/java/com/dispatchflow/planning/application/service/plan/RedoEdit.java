package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Makes the last undone edit of a ready plan again (FR-145). It asks the validator about {@code
 * Plan.afterRedo} before its transaction (DL §6.3, ADR-112).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M4.
 */
public final class RedoEdit {

    private final PlanRepository plans;
    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param assembler builds the evaluation request
     * @param checker the optimizer's validator
     * @param clock the time, for {@code today}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RedoEdit(
            PlanRepository plans,
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            Clock clock,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.plans = plans;
        this.assembler = assembler;
        this.checker = checker;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Redoes the edit.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, {@code NothingToRedo}, {@code StaleEvaluation}, or {@code ValidatorUnavailable}
     */
    public void redo(Actor actor, PlanId plan, Version expected) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
