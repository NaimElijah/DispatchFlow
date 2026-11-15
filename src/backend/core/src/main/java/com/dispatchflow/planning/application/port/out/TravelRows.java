package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.util.List;

/**
 * Base road travel times and distances from some points to others, as one OSRM {@code table} call
 * with sources and destinations answers them (DL §10.2, TS §6.4, ADR-052). A re-optimization asks for
 * them from each vehicle's latest position to every location of the plan's stored matrix, depots
 * included, and adds them to that matrix as rows only (ADR-050, ADR-052). They are base durations only, as in {@link TravelMatrix}: the
 * optimizer applies the bucket factors and the buffer (FR-321, FR-322).
 *
 * <p>Both lists are row-major over {@code sources × targets}: the entry for the pair {@code (source,
 * target)} is at {@code source * targets.size() + target}. A pair with no road route holds {@link
 * TravelMatrix#NO_ROUTE} (FR-66, ADR-052). Its points are drivers' and customers' places, so it prints
 * redacted.
 *
 * <p>Built in M9.
 *
 * @param sources the points the rows start from, in the order the rows follow
 * @param targets the points the rows reach, in the order each row's entries follow
 * @param seconds the base travel seconds of each pair, row-major
 * @param metres the road metres of each pair, row-major
 */
public record TravelRows(List<GeoPoint> sources, List<GeoPoint> targets, List<Integer> seconds, List<Integer> metres)
        implements PersonalData {

    /** Copies the lists. */
    public TravelRows {
        sources = List.copyOf(sources);
        targets = List.copyOf(targets);
        seconds = List.copyOf(seconds);
        metres = List.copyOf(metres);
    }

    /**
     * A form that hides the points.
     *
     * @return {@code TravelRows[redacted]}
     */
    @Override
    public String toString() {
        return "TravelRows[redacted]";
    }
}
