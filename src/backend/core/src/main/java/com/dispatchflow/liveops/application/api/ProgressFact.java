package com.dispatchflow.liveops.application.api;

import com.dispatchflow.liveops.application.api.StopArrivalFact.RouteProgress;
import com.dispatchflow.liveops.application.api.StopArrivalFact.StopProgress;
import java.util.Objects;

/**
 * How far a route and one of its stops have come, read from Live Operations' own records whatever
 * the plan holds now (FR-354, FR-373). Messaging translates both into the {@code RouteProgress}
 * and {@code StopProgress} its {@code StopMessages.onDeliveryChanged} takes, for a stop the plan
 * may no longer hold.
 *
 * <p>Built in M8.
 *
 * @param route how far the route has come
 * @param stop how far the stop has come
 */
public record ProgressFact(RouteProgress route, StopProgress stop) {

    /** Checks both parts are present. */
    public ProgressFact {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(stop, "stop");
    }
}
