package com.dispatchflow.planning.application.spi;

import com.dispatchflow.shared.RouteId;
import java.util.List;
import java.util.Objects;

/**
 * A route's last positions, as a re-optimization places its vehicle (ADR-050 point 2, BR-79,
 * BR-80).
 *
 * <p>Built in M9.
 *
 * @param route the route
 * @param lastTwo its last two kept positions, the latest last; fewer when fewer were kept
 */
public record RoutePositionsFact(RouteId route, List<PositionFact> lastTwo) {

    /** Checks the route is present, and copies the positions. */
    public RoutePositionsFact {
        Objects.requireNonNull(route, "route");
        lastTwo = List.copyOf(lastTwo);
    }
}
