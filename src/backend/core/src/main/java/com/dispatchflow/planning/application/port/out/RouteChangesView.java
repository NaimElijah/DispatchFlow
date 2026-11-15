package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.List;
import java.util.Objects;

/**
 * What changed on one route between a revision the driver saw and the current one: the stops added,
 * removed and reordered, which either app shows after a route change (FR-249, FR-97, ADR-059). The
 * driver's day itself is Live Operations' query (placement P6).
 *
 * <p>Built in M10.
 *
 * @param route the route
 * @param from the revision compared from, the one the driver saw
 * @param to the plan's current revision
 * @param added the stops on the route now that weren't in {@code from}, in the route's order
 * @param removed the stops in {@code from} that aren't on the route now, in their old order
 * @param reordered the stops on both whose place among the kept stops changed, in the route's order
 */
public record RouteChangesView(
        RouteId route,
        RevisionNo from,
        RevisionNo to,
        List<StopId> added,
        List<StopId> removed,
        List<StopId> reordered) {

    /** Checks every part is present, and copies the lists. */
    public RouteChangesView {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        reordered = List.copyOf(reordered);
    }
}
