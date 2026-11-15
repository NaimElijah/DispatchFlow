package com.dispatchflow.liveops.application.port.out;

/**
 * Where a route stands on the screens: <em>not started</em>, <em>in progress</em> or
 * <em>finished</em> (FR-244). A route with no {@code RouteRun} is not started, which the domain's
 * {@code RunStatus} doesn't hold, so the views name the three here.
 *
 * <p>Built in M5.
 */
public enum RunState {
    /** The route has no run. */
    NOT_STARTED,
    /** The run started and has not finished. */
    IN_PROGRESS,
    /** The run finished, perhaps with stops pending (FR-329). */
    FINISHED
}
