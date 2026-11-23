package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.shared.Version;
import java.util.Optional;

/**
 * What a test sees of {@link PlanningSettings}, read through their getters: a value that compares by
 * every part. Tests compare settings with the values they expect, or after a round trip through the
 * database, through it.
 *
 * @param version the version they were stored at, or empty when they were never saved
 * @param travelBuckets the travel-time buckets
 * @param buffer the travel-time buffer
 * @param defaultSolveLimit the default daily solve limit
 * @param fixedVehicleCost the fixed cost per vehicle used
 * @param departureAllowance the departure allowance
 */
public record PlanningSettingsDescription(
        Optional<Version> version,
        TravelBuckets travelBuckets,
        Buffer buffer,
        SolveLimit defaultSolveLimit,
        FixedVehicleCost fixedVehicleCost,
        DepartureAllowance departureAllowance) {

    /**
     * Describes settings.
     *
     * @param settings the settings
     * @return what their getters give
     */
    public static PlanningSettingsDescription of(PlanningSettings settings) {
        return new PlanningSettingsDescription(
                settings.savedVersion(),
                settings.travelBuckets(),
                settings.buffer(),
                settings.defaultSolveLimit(),
                settings.fixedVehicleCost(),
                settings.departureAllowance());
    }

    /**
     * The same description at another stored version, as a test expects settings back after saving
     * them.
     *
     * @param stored the version they are expected at
     * @return the description with that version
     */
    public PlanningSettingsDescription at(Version stored) {
        return new PlanningSettingsDescription(
                Optional.of(stored), travelBuckets, buffer, defaultSolveLimit, fixedVehicleCost, departureAllowance);
    }
}
