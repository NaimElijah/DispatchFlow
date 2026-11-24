package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.events.SolveFailed;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Returns a plan whose daily solve failed to ready if it was solved before, and otherwise to draft,
 * with its routes as they were (BR-57, BR-59, FR-65, FR-222, FR-225). Its listener calls it on {@link
 * SolveFailed}. A failed re-optimization changes no plan (BR-26).
 *
 * <p>It is idempotent by nature: {@code Plan.solveFailed} ignores a job the plan no longer waits for,
 * an abandoned plan included (ADR-053). It writes its audit record with the listener's server step (ADR-110).
 *
 * <p>{@link RecordSolveFailureChange} holds the transaction, which records the failure and writes the
 * audit record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>Built in M0.
 */
public final class RecordSolveFailure {

    private final LiveUpdates liveUpdates;
    private final RecordSolveFailureChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens, so the failure shows without a reload (FR-65)
     * @param change the failure's transaction
     */
    public RecordSolveFailure(LiveUpdates liveUpdates, RecordSolveFailureChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Records the failure on the plan.
     *
     * <p>Built in M0.
     *
     * @param event the job's failure
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void on(SolveFailed event, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
