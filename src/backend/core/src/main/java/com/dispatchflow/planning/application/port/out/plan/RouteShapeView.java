package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.planning.domain.plan.RouteIndex;
import com.dispatchflow.shared.RouteId;
import java.util.Objects;

/**
 * One route's line along the road network, for the map, with the index that gives its colour slot and
 * label (FR-24, FR-148, FR-240).
 *
 * <p>Built in M3.
 *
 * @param route the route
 * @param index its index from 1 to 30
 * @param shape its road line, which prints redacted
 */
public record RouteShapeView(RouteId route, RouteIndex index, RouteShape shape) {

    /** Checks every part is present. */
    public RouteShapeView {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(shape, "shape");
    }
}
