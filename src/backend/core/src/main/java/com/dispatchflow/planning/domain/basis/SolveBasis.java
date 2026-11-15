package com.dispatchflow.planning.domain.basis;

import com.dispatchflow.shared.JobId;
import java.time.Instant;
import java.util.Objects;

/**
 * The solve a plan is waiting for or was computed from: its job, and when it was requested (DL §17
 * "Basis", BR-59, BR-71).
 *
 * @param job the solve job; only its result may end the plan's solve
 * @param requestedAt when the solve was requested
 */
public record SolveBasis(JobId job, Instant requestedAt) {

    /** Checks both parts are present. */
    public SolveBasis {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
