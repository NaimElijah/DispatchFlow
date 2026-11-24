package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import java.time.Instant;
import java.util.Objects;

/**
 * Where a solve job is, so the dispatcher sees that it is queued or building its matrix, how long its
 * search has run out of its limit, any overrun past it, and why it failed (FR-16, FR-65, FR-142,
 * FR-222). The phase carries its own times; the screen measures from {@code asOf}, the server's time
 * of the answer, so a phone's clock doesn't skew it.
 *
 * <p>To Be Implemented.
 *
 * @param job the job
 * @param plan the plan it solves
 * @param lane its lane
 * @param solver its solver (FR-20)
 * @param limit its search limit (FR-19, BR-84)
 * @param requestedAt when it was requested
 * @param phase where it is, with the failure for a failed job (FR-65)
 * @param asOf the server's time when the view was read
 */
public record SolveProgressView(
        JobId job,
        PlanId plan,
        Lane lane,
        SolverName solver,
        SolveLimit limit,
        Instant requestedAt,
        SolveJob.Phase phase,
        Instant asOf) {

    /** Checks every part is present. */
    public SolveProgressView {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(solver, "solver");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(asOf, "asOf");
    }
}
