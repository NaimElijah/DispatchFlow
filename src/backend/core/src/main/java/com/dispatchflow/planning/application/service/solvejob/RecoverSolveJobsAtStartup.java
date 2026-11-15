package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.shared.application.Clock;

/**
 * Startup recovery, before the lane workers start: a job that was building its matrix or running
 * fails as <em>interrupted</em>, a queued re-optimization fails too, and a queued daily solve stays
 * queued for its worker (BR-74, BR-85, NFR-12, DL §12). Each job changes in a transaction of its own.
 *
 * <p>Built in M4.
 */
public final class RecoverSolveJobsAtStartup {

    private final SolveJobRepository jobs;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param jobs where solve jobs are kept
     * @param clock the time of each failure
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2, ADR-110)
     */
    public RecoverSolveJobsAtStartup(SolveJobRepository jobs, Clock clock, AuditLog auditLog) {
        this.jobs = jobs;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Recovers the jobs a restart interrupted.
     *
     * <p>Built in M4.
     *
     * @param call the startup step
     */
    public void recover(CallContext call) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
