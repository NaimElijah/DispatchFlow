package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.planning.domain.solution.PinTarget;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Pins an order to a vehicle or a driver in a draft or ready plan, so the next solve serves it there
 * or drops it with that reason (FR-91, FR-228). It replaces any pin the order had, and asks the
 * validator about {@code Plan.afterPin} before its transaction (DL §6.3, ADR-112).
 *
 * <p>Built in M4.
 */
public final class PinOrder {

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
    public PinOrder(
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
     * Pins the order.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param order the order
     * @param target the vehicle or driver
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotDraftOrReady}, {@code
     *     DateInPast}, {@code StaleEvaluation}, or {@code ValidatorUnavailable}
     */
    public void pin(Actor actor, PlanId plan, OrderId order, PinTarget target, Version expected) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
