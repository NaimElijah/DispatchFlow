package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.shared.application.Clock;

/**
 * The queue watchdog, every 15 seconds: it fails each job past its phase's deadline, 5 minutes queued,
 * 2 minutes building its matrix, or its limit plus 30 seconds of search, so no plan stays solving
 * (BR-32, BR-84, BR-112, DL §12, ADR-101). {@code SolveJob.expireIfOverdue} decides, and changes
 * nothing for a job within its deadline. Each job changes in a transaction of its own; one that ended
 * meanwhile refuses with {@code JobAlreadyEnded}, and the watchdog moves on (DL §11).
 *
 * <p>Built in M4.
 */
public final class ExpireOverdueSolveJobs {

    private final SolveJobRepository jobs;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param jobs where solve jobs are kept
     * @param clock the watchdog's time
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2, ADR-110)
     */
    public ExpireOverdueSolveJobs(SolveJobRepository jobs, Clock clock, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.jobs = jobs;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Fails every overdue job.
     *
     * <p>Built in M4.
     *
     * @param call the job's server step
     * @return how many jobs it failed
     */
    public int expireOverdue(CallContext call) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
