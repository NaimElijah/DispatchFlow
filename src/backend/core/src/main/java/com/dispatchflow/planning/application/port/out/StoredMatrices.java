package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import java.util.Optional;

/**
 * The travel matrix each plan was solved with, stored with the plan, compressed, beside its list of
 * locations (BR-37, TS §6.5, ADR-052). An edit check, the publishing re-check and a
 * re-optimization reuse it, so they don't call OSRM again, and a solve over the same locations
 * reuses it too.
 *
 * <p>Built in M3.
 */
public interface StoredMatrices {

    /**
     * Stores the matrix a solve job built for its plan, in the caller's transaction: {@code
     * RunSolveJob}'s success transaction for a daily job, so a failed job stores none. It replaces the
     * plan's earlier matrix, which the job's own stored input still records (DL §9.7).
     *
     * <p>Built in M3.
     *
     * @param plan the plan
     * @param job the solve job that built it
     * @param matrix the matrix
     */
    void store(PlanId plan, JobId job, TravelMatrix matrix);

    /**
     * The matrix the plan's latest solve was built with.
     *
     * <p>Built in M3.
     *
     * @param plan the plan
     * @return the matrix, or empty when the plan was never solved
     */
    Optional<TravelMatrix> of(PlanId plan);
}
