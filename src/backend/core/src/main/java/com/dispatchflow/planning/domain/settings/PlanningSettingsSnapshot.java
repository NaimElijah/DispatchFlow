package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * {@link PlanningSettings}' whole state, which the repository stores and {@link
 * PlanningSettings#restore} rebuilds. The repository rebuilds each value through {@code
 * StoredStateInvalid.whileRestoring}, so a stored value a check now refuses is an incident.
 *
 * @param version the version it was loaded at, or null for an aggregate never saved
 *     (ADR-126)
 * @param travelBuckets the travel-time buckets
 * @param buffer the travel-time buffer
 * @param defaultSolveLimit the default daily solve limit
 * @param fixedVehicleCost the fixed cost per vehicle used
 * @param departureAllowance the departure allowance
 */
public record PlanningSettingsSnapshot(
        @Nullable Version version,
        TravelBuckets travelBuckets,
        Buffer buffer,
        SolveLimit defaultSolveLimit,
        FixedVehicleCost fixedVehicleCost,
        DepartureAllowance departureAllowance) {

    /** Checks every part is present. */
    public PlanningSettingsSnapshot {
        Objects.requireNonNull(travelBuckets, "travelBuckets");
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(defaultSolveLimit, "defaultSolveLimit");
        Objects.requireNonNull(fixedVehicleCost, "fixedVehicleCost");
        Objects.requireNonNull(departureAllowance, "departureAllowance");
    }
}
