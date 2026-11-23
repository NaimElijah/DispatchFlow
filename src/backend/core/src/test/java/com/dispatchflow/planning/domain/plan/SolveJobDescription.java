package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.solution.SolveResult;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Optional;

/**
 * What a test sees of a {@link SolveJob}, read through its getters: a value that compares by every
 * part. Tests compare a job before and after a refused command, or after a round trip through the
 * database, through it.
 *
 * @param id the job's ID
 * @param version the version it was stored at, or empty when it was never saved
 * @param plan the plan it solves
 * @param lane its lane
 * @param solver its solver
 * @param limit its search limit
 * @param seed its seed
 * @param basisRevision the revision a re-optimization starts from
 * @param requestedAt when it was requested
 * @param phase where it is in its lifecycle
 * @param result a succeeded daily job's result
 */
public record SolveJobDescription(
        JobId id,
        Optional<Version> version,
        PlanId plan,
        Lane lane,
        SolverName solver,
        SolveLimit limit,
        Seed seed,
        Optional<RevisionNo> basisRevision,
        Instant requestedAt,
        SolveJob.Phase phase,
        Optional<SolveResult> result) {

    /**
     * Describes a job.
     *
     * @param job the job
     * @return what its getters give
     */
    public static SolveJobDescription of(SolveJob job) {
        return new SolveJobDescription(
                job.id(),
                job.savedVersion(),
                job.plan(),
                job.lane(),
                job.solver(),
                job.limit(),
                job.seed(),
                job.basisRevision(),
                job.requestedAt(),
                job.phase(),
                job.result());
    }

    /**
     * The same description at another stored version, as a test expects a job back after saving it.
     *
     * @param stored the version it is expected at
     * @return the description with that version
     */
    public SolveJobDescription at(Version stored) {
        return new SolveJobDescription(
                id, Optional.of(stored), plan, lane, solver, limit, seed, basisRevision, requestedAt, phase, result);
    }
}
