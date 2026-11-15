package com.dispatchflow.planning.application.port.out;

import java.util.Objects;

/**
 * The road network, OSRM, couldn't be reached or refused the call (FR-65, TS §6.4). It is not a
 * domain refusal: a solve job's worker fails the job as <em>road network unavailable</em>, an
 * evaluation's caller asks {@code FeasibilityChecker.unavailableFor} instead, and a query lets it
 * fail the call. Its message and its cause never hold a point (NFR-19): the adapter wraps the I/O
 * error without the HTTP client's message, which holds the URL's coordinates.
 *
 * <p>Built in M3.
 */
public final class RoadNetworkUnavailable extends RuntimeException {

    /**
     * Creates the error.
     *
     * @param reason what failed, such as a timeout or OSRM's status code; never a point
     */
    public RoadNetworkUnavailable(String reason) {
        super(Objects.requireNonNull(reason, "reason"));
    }
}
