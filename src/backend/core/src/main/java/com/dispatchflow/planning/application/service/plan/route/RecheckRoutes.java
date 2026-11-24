package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;

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
 * <p>It loops over the plans {@link RecheckRoutesChange#due} lists. For each it reads the plan
 * through {@link RecheckRoutesChange#layoutOf}, builds the evaluation request and asks the
 * validator; {@link RecheckRoutesChange#apply} then holds the plan's transaction, and after it
 * commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class RecheckRoutes {

    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final LiveUpdates liveUpdates;
    private final RecheckRoutesChange change;

    /**
     * Creates the job.
     *
     * @param assembler builds each evaluation request
     * @param checker the optimizer's validator
     * @param liveUpdates the dispatchers' open screens
     * @param change each plan's transaction
     */
    public RecheckRoutes(
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            LiveUpdates liveUpdates,
            RecheckRoutesChange change) {
        this.assembler = assembler;
        this.checker = checker;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Re-checks every plan with an unchecked route.
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many plans had their routes checked
     * @throws com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest when the
     *     optimizer refused the evaluation request as invalid, a defect of the request the core
     *     built, which fails the call
     */
    public int recheckDue(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
