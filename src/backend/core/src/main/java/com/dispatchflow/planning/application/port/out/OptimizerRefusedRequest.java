package com.dispatchflow.planning.application.port.out;

import java.util.Objects;

/**
 * The optimization service refused the core's request: it names a solver the optimizer doesn't hold,
 * gives a budget that solver can't honour, or describes a problem the optimizer finds invalid
 * (ADR-004, ADR-044, ADR-050). It is a defect of the request the core built, or a race with a
 * redeploy that dropped a solver, never an outage and never a domain refusal, so it is no {@code
 * DomainError} and fails loudly. Its message holds the optimizer's code only, never the request's
 * orders or places (NFR-19).
 *
 * <p>Built in M0.
 */
public final class OptimizerRefusedRequest extends RuntimeException {

    private final String code;

    /**
     * Creates the error.
     *
     * @param code the optimizer's refusal code, as its contract names it
     */
    public OptimizerRefusedRequest(String code) {
        super("the optimizer refused the request with " + Objects.requireNonNull(code, "code"));
        this.code = code;
    }

    /**
     * The optimizer's refusal code, for the log and the job's record.
     *
     * @return the code
     */
    public String code() {
        return code;
    }
}
