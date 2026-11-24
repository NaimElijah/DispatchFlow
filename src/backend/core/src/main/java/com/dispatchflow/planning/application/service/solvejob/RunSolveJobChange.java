package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.StoredMatrices;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.proposal.ProposalRepository;
import com.dispatchflow.planning.application.port.out.solvejob.DailyAnswer;
import com.dispatchflow.planning.application.port.out.solvejob.ReoptimizationAnswer;
import com.dispatchflow.planning.application.port.out.solvejob.ReoptimizationRequest;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveRequest;
import com.dispatchflow.planning.domain.solution.SolveFailure;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The phase changes of {@link RunSolveJob}, each a transaction of its own that saves the job and
 * writes its audit record: the claim, the start of the search, and the job's end, which stores the
 * request's matrix on success and, for a re-optimization, adds its proposal (DL §6.1, §9.2, TS §6.2,
 * ADR-063, ADR-129).
 *
 * <p>Built in M0, in full in M4; its re-optimization's end in M9.
 */
@Transactional
public class RunSolveJobChange {

    private final SolveJobRepository jobs;
    private final PlanRepository plans;
    private final ProposalRepository proposals;
    private final StoredMatrices storedMatrices;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the phase changes.
     *
     * @param jobs where solve jobs are kept, and the lane's queue
     * @param plans where plans are kept, for a daily job's locks
     * @param proposals where a re-optimization's proposal is added
     * @param storedMatrices each plan's stored matrix, which the success transaction writes
     *     (ADR-052, ADR-121)
     * @param clock the time of each phase change
     * @param ids the new proposal's ID
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2, ADR-110)
     */
    public RunSolveJobChange(
            SolveJobRepository jobs,
            PlanRepository plans,
            ProposalRepository proposals,
            StoredMatrices storedMatrices,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.jobs = jobs;
        this.plans = plans;
        this.proposals = proposals;
        this.storedMatrices = storedMatrices;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Claims the lane's oldest queued job, under the lane's lock, so it starts building its matrix
     * (BR-75, DL §9.2).
     *
     * <p>Built in M0.
     *
     * @param lane the worker's lane
     * @param call the worker's server step, which the audit record names (ADR-110)
     * @return the claimed job, with its plan and the plan's date, or empty when the lane had none
     *     queued
     */
    public Optional<Claimed> claim(Lane lane, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Starts the job's search, once its request and matrix are built, so its limit runs from now
     * (FR-19, BR-75).
     *
     * <p>Built in M0.
     *
     * @param job the claimed job
     * @param call the worker's server step, which the audit record names (ADR-110)
     * @throws NotFound when no job has the ID
     * @throws StaleVersion when the job changed between its read and its save; the worker reloads it
     *     and moves on
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code JobAlreadyEnded} when the
     *     watchdog or the midnight job ended it meanwhile
     */
    public void searchStarted(JobId job, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Ends a daily job with the optimizer's answer: it succeeds with the validated solution unless
     * that breaks a lock or no plan could hold it, and stores the request's matrix on success; it
     * fails with the answer's reason otherwise (FR-65, FR-238, ADR-052, ADR-112).
     *
     * <p>Built in M0.
     *
     * @param job the job
     * @param request the request the optimizer was asked, whose matrix a success stores
     * @param answer the optimizer's answer
     * @param call the worker's server step, which the audit record names (ADR-110)
     * @throws NotFound when no job has the ID
     * @throws StaleVersion when the job changed between its read and its save; the worker reloads it
     *     and moves on
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code JobAlreadyEnded} when the
     *     watchdog or the midnight job ended it meanwhile, and the worker drops the answer
     */
    public void endDaily(JobId job, SolveRequest request, DailyAnswer answer, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Ends a re-optimization job with the optimizer's answer: it succeeds with the {@code
     * ReoptimizationResult} built from the answer and the request, adds the proposal and stores the
     * request's new matrix, unless the proposal refuses the result; it fails with the answer's
     * reason otherwise (FR-35, FR-36, ADR-052, ADR-112, ADR-121).
     *
     * <p>Built in M9.
     *
     * @param job the job
     * @param request the request the optimizer was asked, with its fact time, routes and matrix
     * @param answer the optimizer's answer
     * @param call the worker's server step, which the audit record names (ADR-110)
     * @throws NotFound when no job has the ID
     * @throws StaleVersion when the job changed between its read and its save; the worker reloads it
     *     and moves on
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code JobAlreadyEnded} when the
     *     watchdog or the midnight job ended it meanwhile, and the worker drops the answer
     */
    public void endReoptimization(
            JobId job, ReoptimizationRequest request, ReoptimizationAnswer answer, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * Fails a job that got no answer to end with: an unreachable road network, a date with no
     * orders, or a request the optimizer refused (FR-65, ADR-121).
     *
     * <p>Built in M0.
     *
     * @param job the job
     * @param failure why it failed
     * @param call the worker's server step, which the audit record names (ADR-110)
     * @throws NotFound when no job has the ID
     * @throws StaleVersion when the job changed between its read and its save; the worker reloads it
     *     and moves on
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code JobAlreadyEnded} when the
     *     watchdog or the midnight job ended it meanwhile
     */
    public void fail(JobId job, SolveFailure failure, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * A job the worker claimed, with what the worker needs for its request and the screens.
     *
     * @param job the job
     * @param plan the plan it solves
     * @param date the plan's delivery date
     */
    public record Claimed(JobId job, PlanId plan, DeliveryDate date) {

        /** Checks every part is present. */
        public Claimed {
            Objects.requireNonNull(job, "job");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(date, "date");
        }
    }
}
