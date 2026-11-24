package com.dispatchflow.planning.application.port.out;

import java.util.Objects;

/**
 * The optimization service couldn't be reached, didn't answer within the client timeout, or gave an
 * answer the adapter doesn't know (FR-65, ADR-051). It is not a domain refusal, and a query lets it
 * fail the call. A solve or re-optimization request that can't list the solvers goes on unchecked,
 * and its job fails as <em>optimizer unavailable</em>. A solve or a re-optimization never throws
 * it: {@code RouteOptimizer} answers its {@code Failed} instead, and {@code FeasibilityChecker} an
 * unavailable evaluation. Its message never holds the request's orders or places (NFR-19).
 *
 * <p>To Be Implemented.
 */
public final class OptimizerUnavailable extends RuntimeException {

    /**
     * Creates the error.
     *
     * @param reason what failed, such as a timeout or the optimizer's status code; never the
     *     request's contents
     */
    public OptimizerUnavailable(String reason) {
        super(Objects.requireNonNull(reason, "reason"));
    }
}
