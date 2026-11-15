package com.dispatchflow.planning.application.api;

import java.time.Duration;

/**
 * The planning settings other modules read (FR-67, FR-183): today the departure allowance, which
 * Live Operations' {@code StartDeadline} adds to a shift's start (FR-101).
 *
 * <p>Built in M8.
 */
public interface PlanningSettingsFacts {

    /**
     * The departure allowance: the time between a shift's start and leaving the depot, for loading
     * (FR-67).
     *
     * <p>Built in M8.
     *
     * @return the allowance, in whole minutes, zero or more
     */
    Duration departureAllowance();
}
