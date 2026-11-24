package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link ExpireOverdueSolveJobs}: a read-only one that lists the unfinished
 * jobs, and one per job that fails it with {@code SolveJob.expireIfOverdue} when it is past its
 * phase's deadline, saves it and writes the audit record (TS §6.2, ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class ExpireOverdueSolveJobsChange {

    private final SolveJobRepository jobs;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param jobs where solve jobs are kept
     * @param clock the watchdog's time
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2, ADR-110)
     */
    public ExpireOverdueSolveJobsChange(SolveJobRepository jobs, Clock clock, AuditLog auditLog) {
        this.jobs = jobs;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The jobs still queued, building their matrix or searching.
     *
     * <p>To Be Implemented.
     *
     * @return the jobs, possibly none
     */
    @Transactional(readOnly = true)
    public List<JobId> unfinished() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Fails one job if it is past its phase's deadline.
     *
     * <p>To Be Implemented.
     *
     * @param job the job
     * @param call the job's server step
     * @return the job's plan when it failed the job, for the dispatchers' open screens; empty when the
     *     job is within its deadline
     * @throws NotFound when no job has the ID
     * @throws StaleVersion when the job changed between its read and its save; the watchdog reloads
     *     it and moves on
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code JobAlreadyEnded} when the
     *     job ended meanwhile; the watchdog moves on
     */
    public Optional<PlanId> apply(JobId job, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
