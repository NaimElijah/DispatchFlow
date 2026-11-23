package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.StoredMatrices;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.proposal.ProposalRepository;
import com.dispatchflow.planning.application.port.out.solvejob.RouteOptimizer;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * A lane's worker step: it claims the lane's oldest queued job, builds its request and matrix,
 * starts the search, calls the optimizer, and ends the job (FR-16, FR-65, FR-142, FR-238, BR-32,
 * BR-75, NFR-11, DL §6.2, §9.2, ADR-051). One worker per lane runs it, one job at a time, on a
 * virtual thread; each phase change is a transaction of its own, and the optimizer call runs
 * outside any.
 *
 * <ul>
 *   <li>A daily job's validated solution is checked against the plan's locks first ({@code
 *       Plan.locksBrokenBy}), because the request doesn't send the locks until the optimizer has a
 *       lock rule (ADR-112, ADR-121): a broken lock fails the job with {@code
 *       SolveFailure.lockNotHonoured} (FR-238). Otherwise {@code SolveJob.succeed} keeps it, and a
 *       result no plan could hold ({@code OrderAlreadyInPlan}, {@code VehicleRoutedTwice}, {@code
 *       DriverRoutedTwice}) fails the job with {@code SolveFailure.rejected} instead (ADR-112).
 *   <li>From M3, a job's success transaction also stores the matrix its request was built with,
 *       through {@link StoredMatrices#store}, so a failed job stores none and the assembler only
 *       reads (ADR-052, BR-37). For a daily job that is its matrix; for a re-optimization, the new
 *       matrix the assembler fetched when the stored one lacked a location, which replaces the
 *       stored one (ADR-121). A matrix over the stored one's list leaves it as it is.
 *   <li>A re-optimization's answer becomes a {@code ReoptimizationResult}, which this step builds
 *       from the answer and the request: the request's fact time, each withdrawn vehicle's cause
 *       from its {@code VehicleState}, and the per-driver changes and goods hand-overs against the
 *       request's routes (see {@code ReoptimizationAnswer}). {@code SolveJob.succeed} then creates
 *       the proposal in the job's transaction (DL §6.1). A result the proposal refuses, from {@code
 *       ReoptimizationProposal.create} ({@code OrderAlreadyInPlan}, {@code VehicleRoutedTwice} or
 *       {@code DriverRoutedTwice}), fails the job with {@code SolveFailure.rejected} instead,
 *       naming the broken rule as for a daily result (ADR-112). The step saves the job before it
 *       adds the proposal: adding it writes at once everything the transaction holds, so the job's
 *       change written there would make the job's own save fail.
 *   <li>An optimizer answer of failure, an unreachable road network ({@code
 *       RoadNetworkUnavailable}) or a date with no orders fails the job with its reason (FR-65). A
 *       request the optimizer refused ({@code OptimizerRefusedRequest}) fails the job with {@code
 *       REQUEST_REFUSED} (ADR-121), and the step logs it as a defect.
 *   <li>A job the watchdog or the midnight job ended meanwhile refuses with {@code
 *       JobAlreadyEnded}, and the worker drops its answer (DL §11).
 *   <li>A lost version race on {@code SolveJobRepository.save} is no error of this step: it reloads
 *       the job, finds it ended ({@code JobAlreadyEnded}), and moves on to the next job (DL §11).
 *       It never retries its own change.
 * </ul>
 *
 * <p>The job's events, {@code SolveSucceeded} or {@code SolveFailed}, then end the plan's solve
 * (BR-59).
 *
 * <p><b>Domain gap.</b> {@code SolveJob.succeed} keeps no objective and no stats, which the daily
 * answer carries for NFR-11: M0 adds them to {@code SolveResult} (see {@code DailyAnswer.Solved}).
 * {@code SolveJob.fail} keeps no stats either, which a {@code Failed} answer carries when the
 * optimizer answered: M0 adds them beside the failure (see {@code DailyAnswer.Failed}).
 *
 * <p>Built in M0; in full in M4. M0 builds the daily lane, and the roadmap's M4 builds "the {@code
 * solve_job} queue, one worker per lane" (ADR-051).
 */
public final class RunSolveJob {

    private final SolveJobRepository jobs;
    private final PlanRepository plans;
    private final ProposalRepository proposals;
    private final SolveRequestAssembler assembler;
    private final RouteOptimizer optimizer;
    private final StoredMatrices storedMatrices;
    private final Clock clock;
    private final IdGenerator ids;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the worker step.
     *
     * @param jobs where solve jobs are kept, and the lane's queue
     * @param plans where plans are kept, for a daily job's locks
     * @param proposals where a re-optimization's proposal is added
     * @param assembler builds the job's request and matrix
     * @param optimizer the optimization service
     * @param storedMatrices each plan's stored matrix, which the success transaction writes
     *     (ADR-052, ADR-121)
     * @param clock the time of each phase change
     * @param ids the new proposal's ID
     * @param liveUpdates the dispatchers' open screens (FR-142, FR-222)
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2, ADR-110)
     */
    public RunSolveJob(
            SolveJobRepository jobs,
            PlanRepository plans,
            ProposalRepository proposals,
            SolveRequestAssembler assembler,
            RouteOptimizer optimizer,
            StoredMatrices storedMatrices,
            Clock clock,
            IdGenerator ids,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.jobs = jobs;
        this.plans = plans;
        this.proposals = proposals;
        this.assembler = assembler;
        this.optimizer = optimizer;
        this.storedMatrices = storedMatrices;
        this.clock = clock;
        this.ids = ids;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Runs the lane's next queued job to its end.
     *
     * <p>Built in M0.
     *
     * @param lane the worker's lane
     * @param call the worker's server step, which the audit records name (ADR-110)
     * @return {@code true} when it ran a job, {@code false} when the lane had none queued
     */
    public boolean runNext(Lane lane, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
