package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link SolveJob}'s whole state, which the repository stores and {@link SolveJob#restore} rebuilds.
 *
 * @param id the job
 * @param version the version it was loaded at
 * @param plan the plan it solves
 * @param lane its lane
 * @param solver its solver
 * @param limit its search limit
 * @param seed its seed
 * @param basisRevision the revision a re-optimization started from
 * @param requestedAt when it was requested
 * @param phase where it is in its lifecycle
 * @param result a succeeded daily job's result
 */
public record SolveJobSnapshot(
        JobId id,
        Version version,
        PlanId plan,
        Lane lane,
        SolverName solver,
        SolveLimit limit,
        Seed seed,
        Optional<RevisionNo> basisRevision,
        Instant requestedAt,
        SolveJob.Phase phase,
        Optional<SolveResult> result) {

    /** Checks every part is present, possibly empty. */
    public SolveJobSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(solver, "solver");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(basisRevision, "basisRevision");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(result, "result");
    }
}
