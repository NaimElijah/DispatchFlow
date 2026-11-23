package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;

/**
 * The queue watchdog, every 15 seconds: it fails each job past its phase's deadline, 5 minutes queued,
 * 2 minutes building its matrix, or its limit plus 30 seconds of search, so no plan stays solving
 * (BR-32, BR-84, BR-112, DL §12, ADR-101). {@code SolveJob.expireIfOverdue} decides, and changes
 * nothing for a job within its deadline. Each job changes in a transaction of its own; one that ended
 * meanwhile refuses with {@code JobAlreadyEnded}, and the watchdog moves on (DL §11). A lost version
 * race on {@code SolveJobRepository.save} is no error of the watchdog either: it reloads the job,
 * finds it ended ({@code JobAlreadyEnded}), and moves on to the next job, never retrying its change
 * (DL §11).
 *
 * <p>It loops over the jobs {@link ExpireOverdueSolveJobsChange#unfinished} lists, and {@link
 * ExpireOverdueSolveJobsChange} holds each job's transaction; after each commits, the dispatchers'
 * open screens are told of a job it failed (ADR-129).
 *
 * <p>Built in M4.
 */
public final class ExpireOverdueSolveJobs {

    private final LiveUpdates liveUpdates;
    private final ExpireOverdueSolveJobsChange change;

    /**
     * Creates the job.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change each job's transaction
     */
    public ExpireOverdueSolveJobs(LiveUpdates liveUpdates, ExpireOverdueSolveJobsChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
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
