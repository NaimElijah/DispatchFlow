package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.AccountId;
import java.util.Objects;

/**
 * A route's driver at a report's corrected time and its driver now, which Live Operations'
 * {@code RouteRun.start} and {@code RouteRun.finish} take (FR-68, FR-329, ADR-114).
 *
 * <p>Built in M5.
 *
 * @param driver the route's driver in the revision in force at the corrected time
 * @param currentDriver the route's driver in the current revision
 */
public record RouteDriversFact(AccountId driver, AccountId currentDriver) {

    /** Checks both drivers are present. */
    public RouteDriversFact {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(currentDriver, "currentDriver");
    }
}
