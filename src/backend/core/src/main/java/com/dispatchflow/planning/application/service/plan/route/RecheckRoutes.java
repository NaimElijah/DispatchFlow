package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.application.Clock;

/**
 * The route re-check job, every minute while a ready or published plan has a route <em>not yet
 * checked</em>: it evaluates each such plan as it is and records the check with {@code
 * Plan.recordRouteCheck}, which changes no time or stop (FR-304, FR-395, NFR-42, DL §12, ADR-058).
 *
 * <p>Each plan is evaluated outside any transaction, then changed in a transaction of its own. A
 * plan that changed in between refuses with {@code StaleEvaluation} or {@code StaleVersion}, and is
 * left for the next run, which evaluates it again; an optimizer still down leaves it unchecked the
 * same way. So does a road network that can't be reached for a plan's matrix: the assembler throws
 * {@code RoadNetworkUnavailable}, the job asks {@code FeasibilityChecker.unavailableFor} for the
 * plan's version, and the plan's routes stay <em>not yet checked</em> (FR-395).
 *
 * <p>Built in M9.
 */
public final class RecheckRoutes {

    private final PlanRepository plans;
    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param plans where plans are kept, and which have unchecked routes
     * @param assembler builds each evaluation request
     * @param checker the optimizer's validator
     * @param clock the time, for the audit record
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2)
     */
    public RecheckRoutes(
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
     * Re-checks every plan with an unchecked route.
     *
     * <p>Built in M9.
     *
     * @param call the job's server step
     * @return how many plans had their routes checked
     * @throws com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest when the
     *     optimizer refused the evaluation request as invalid, a defect of the request the core
     *     built, which fails the call
     */
    public int recheckDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
