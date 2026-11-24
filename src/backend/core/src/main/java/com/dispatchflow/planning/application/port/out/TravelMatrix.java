package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.util.List;

/**
 * Base road travel times and distances between distinct locations, as OSRM's {@code table} service
 * answers them (DL §10.2, TS §6.4, ADR-052). They are base durations only: the optimizer's {@code
 * TravelTimeModel} applies the bucket factors and the buffer, and the core never does (FR-321,
 * FR-322). A plan stores the matrix it was solved with, so its history keeps its values (BR-37).
 *
 * <p>Both lists are row-major over {@code locations}: the entry for the pair {@code (from, to)} is at
 * {@code from * locations.size() + to}. A pair with no road route holds {@link #NO_ROUTE}, so the optimizer keeps
 * the orders there out of the search and names the reason (FR-66, ADR-052). Its locations are
 * customers' places, so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param locations the distinct locations, each once, in the order the rows and columns follow
 * @param seconds the base travel seconds of each pair, row-major
 * @param metres the road metres of each pair, row-major
 */
public record TravelMatrix(List<GeoPoint> locations, List<Integer> seconds, List<Integer> metres)
        implements PersonalData {

    /** The value a pair with no road route holds, in both lists (ADR-052). */
    public static final int NO_ROUTE = -1;

    /** Copies the lists. */
    public TravelMatrix {
        locations = List.copyOf(locations);
        seconds = List.copyOf(seconds);
        metres = List.copyOf(metres);
    }

    /**
     * A form that hides the locations.
     *
     * @return {@code TravelMatrix[redacted]}
     */
    @Override
    public String toString() {
        return "TravelMatrix[redacted]";
    }
}
