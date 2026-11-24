package com.dispatchflow.reporting.application.port.out;

import java.util.Objects;

/**
 * The optimization service couldn't be reached for a baseline, didn't answer within the client
 * timeout, or gave an answer the adapter doesn't know, such as a failure kind with no {@link
 * RefusalCause} (FR-39, ADR-121 P25). It is Reporting's own name for the outage, since Reporting
 * reads no Planning type (ADR-120 P5). It is not a domain refusal: it fails the listener's
 * delivery, so Spring Modulith resubmits it (ADR-064, DL §12). Its message never holds the
 * request's orders or places (NFR-19).
 *
 * <p>To Be Implemented.
 */
public final class BaselineOptimizerUnavailable extends RuntimeException {

    /**
     * Creates the error.
     *
     * @param reason what failed, such as a timeout or the optimizer's status code; never the
     *     request's contents
     */
    public BaselineOptimizerUnavailable(String reason) {
        super(Objects.requireNonNull(reason, "reason"));
    }
}
