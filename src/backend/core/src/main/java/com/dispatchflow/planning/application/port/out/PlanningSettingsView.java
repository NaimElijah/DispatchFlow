package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.settings.Buffer;
import com.dispatchflow.planning.domain.settings.DepartureAllowance;
import com.dispatchflow.planning.domain.settings.FixedVehicleCost;
import com.dispatchflow.planning.domain.settings.TravelBuckets;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;

/**
 * The planning settings an Admin sees and changes in one place (FR-183, FR-19, FR-21, FR-67, FR-227,
 * FR-321, FR-322, FR-408, placement P7). Before an Admin changes any, they are the starting values.
 *
 * <p>Built in M4.
 *
 * @param version their version, which the next change sends as {@code If-Match} (BR-76);
 *     empty while they are the starting values, never saved
 * @param travelBuckets the travel-time buckets and their factors (FR-321, FR-408)
 * @param buffer the travel-time buffer (FR-227)
 * @param defaultSolveLimit the default daily solve limit (FR-19, FR-183)
 * @param fixedVehicleCost the fixed cost per vehicle used, or off (FR-21)
 * @param departureAllowance the departure allowance (FR-67)
 */
public record PlanningSettingsView(
        Optional<Version> version,
        TravelBuckets travelBuckets,
        Buffer buffer,
        SolveLimit defaultSolveLimit,
        FixedVehicleCost fixedVehicleCost,
        DepartureAllowance departureAllowance) {

    /** Checks every part is present. */
    public PlanningSettingsView {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(travelBuckets, "travelBuckets");
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(defaultSolveLimit, "defaultSolveLimit");
        Objects.requireNonNull(fixedVehicleCost, "fixedVehicleCost");
        Objects.requireNonNull(departureAllowance, "departureAllowance");
    }
}
