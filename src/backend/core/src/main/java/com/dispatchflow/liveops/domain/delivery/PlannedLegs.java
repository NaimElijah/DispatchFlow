package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The legs Planning stored for a route's current revision, which projections propagate delay along
 * without re-applying a factor (DL §10.4, ADR-067).
 *
 * @param plannedDeparture when the route was planned to leave its depot
 * @param stops the route's stops, in planned order
 */
public record PlannedLegs(DaySeconds plannedDeparture, List<PlannedStopLeg> stops) {

    /**
     * Checks the parts and copies the stops.
     *
     * @throws StoredStateInvalid when a stop appears twice
     */
    public PlannedLegs {
        Objects.requireNonNull(plannedDeparture, "plannedDeparture");
        stops = List.copyOf(stops);
        Set<StopId> seen = new HashSet<>();
        for (PlannedStopLeg each : stops) {
            if (!seen.add(each.stop())) {
                throw new StoredStateInvalid(PlannedLegs.class, "a route holds each stop once");
            }
        }
    }
}
