package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.audit.application.api.CallContext;

/**
 * Startup recovery, before the lane workers start: a job that was building its matrix or running
 * fails as <em>interrupted</em>, a queued re-optimization fails too, and a queued daily solve stays
 * queued for its worker (BR-74, BR-85, NFR-12, DL §12). Each job changes in a transaction of its own.
 * A lost version race on {@code SolveJobRepository.save}, such as with the midnight job, is no error
 * of the recovery: it reloads the job, finds it ended ({@code JobAlreadyEnded}), and moves on to the
 * next job, never retrying its change (DL §11).
 *
 * <p>It loops over the jobs {@link RecoverSolveJobsAtStartupChange#unfinished} lists, and {@link
 * RecoverSolveJobsAtStartupChange} holds each job's transaction (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class RecoverSolveJobsAtStartup {

    private final RecoverSolveJobsAtStartupChange change;

    /**
     * Creates the job.
     *
     * @param change each job's transaction
     */
    public RecoverSolveJobsAtStartup(RecoverSolveJobsAtStartupChange change) {
        this.change = change;
    }

    /**
     * Recovers the jobs a restart interrupted.
     *
     * <p>To Be Implemented.
     *
     * @param call the startup step
     */
    public void recover(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
