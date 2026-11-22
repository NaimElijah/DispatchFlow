package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.util.List;

/**
 * A route's line along the road network, which the map draws (FR-24). It passes through customers'
 * places, so it prints redacted.
 *
 * <p>Built in M3.
 *
 * @param line the points of the road line, in driving order, from the start depot to the end depot
 */
public record RouteShape(List<GeoPoint> line) implements PersonalData {

    /** Copies the line. */
    public RouteShape {
        line = List.copyOf(line);
    }

    /**
     * A form that hides the line.
     *
     * @return {@code RouteShape[redacted]}
     */
    @Override
    public String toString() {
        return "RouteShape[redacted]";
    }
}
