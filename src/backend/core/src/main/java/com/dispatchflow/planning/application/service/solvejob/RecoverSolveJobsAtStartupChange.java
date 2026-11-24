package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link RecoverSolveJobsAtStartup}: a read-only one that lists the unfinished
 * jobs, and one per job that fails it as <em>interrupted</em> when a restart cut it off, saves it
 * and writes the audit record (TS §6.2, ADR-063, ADR-129).
 *
 * <p>Built in M4.
 */
@Transactional
public class RecoverSolveJobsAtStartupChange {

    private final SolveJobRepository jobs;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param jobs where solve jobs are kept
     * @param clock the time of each failure
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2, ADR-110)
     */
    public RecoverSolveJobsAtStartupChange(SolveJobRepository jobs, Clock clock, AuditLog auditLog) {
        this.jobs = jobs;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The jobs still queued, building their matrix or searching.
     *
     * <p>Built in M4.
     *
     * @return the jobs, possibly none
     */
    @Transactional(readOnly = true)
    public List<JobId> unfinished() {
        throw new UnsupportedOperationException("Built in M4");
    }

    /**
     * Recovers one job: fails it as <em>interrupted</em> when it was building its matrix or running,
     * or is a queued re-optimization, and leaves a queued daily solve queued (BR-74, BR-85).
     *
     * <p>Built in M4.
     *
     * @param job the job
     * @param call the startup step
     * @throws NotFound when no job has the ID
     * @throws StaleVersion when the job changed between its read and its save; the recovery reloads
     *     it and moves on
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code JobAlreadyEnded} when the
     *     job ended meanwhile; the recovery moves on
     */
    public void apply(JobId job, CallContext call) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
