package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;

/**
 * Tells the dispatchers' open screens that something changed, over the event stream, so they see it
 * without reloading (FR-16, FR-65, FR-222, TS §6.4). Each call says only what changed; the screen
 * then reads it through Planning's queries. A use case calls it after its transaction commits, so a
 * screen never reads a change that rolled back.
 *
 * <p>To Be Implemented.
 */
public interface LiveUpdates {

    /**
     * A solve job was queued, claimed, started its search, succeeded or failed (FR-16, FR-142,
     * FR-222).
     *
     * <p>To Be Implemented.
     *
     * @param job the job
     * @param plan the plan it solves
     */
    void jobChanged(JobId job, PlanId plan);

    /**
     * A plan changed: its state, its routes, its locks, its journal or its revision.
     *
     * <p>To Be Implemented.
     *
     * @param plan the plan
     */
    void planChanged(PlanId plan);

    /**
     * A re-optimization finished, and its proposal waits for a dispatcher (FR-36, FR-255).
     *
     * <p>To Be Implemented.
     *
     * @param proposal the proposal
     * @param plan the plan it would change
     */
    void proposalReady(ProposalId proposal, PlanId plan);
}
