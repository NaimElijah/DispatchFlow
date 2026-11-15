package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.Version;
import java.util.Objects;

/**
 * The company's planning settings, which Admins change (FR-183, FR-21, FR-67, FR-227, FR-321, DL
 * §4.4). There is one, with no ID. Each value checks itself, so a change can only be refused by its
 * value; each change is audited by its use case (FR-186). No change raises an event.
 */
public final class PlanningSettings extends AggregateRoot {

    private TravelBuckets travelBuckets;
    private Buffer buffer;
    private SolveLimit defaultSolveLimit;
    private FixedVehicleCost fixedVehicleCost;
    private DepartureAllowance departureAllowance;

    private PlanningSettings(PlanningSettingsSnapshot state) {
        super(state.version());
        this.travelBuckets = state.travelBuckets();
        this.buffer = state.buffer();
        this.defaultSolveLimit = state.defaultSolveLimit();
        this.fixedVehicleCost = state.fixedVehicleCost();
        this.departureAllowance = state.departureAllowance();
    }

    /**
     * The settings before an Admin changes any: FR-321's buckets, a 10% buffer (FR-227), a 45-second
     * daily limit (FR-19), no fixed vehicle cost (FR-21), and no departure allowance (ADR-101).
     *
     * @return the new settings, at {@code Version.NEW}
     */
    public static PlanningSettings startingValues() {
        return new PlanningSettings(new PlanningSettingsSnapshot(
                Version.NEW,
                TravelBuckets.STARTING,
                Buffer.STARTING,
                SolveLimit.DEFAULT_DAILY,
                new FixedVehicleCost.Off(),
                DepartureAllowance.STARTING));
    }

    /**
     * Rebuilds the stored settings; only the persistence adapter calls it. Every value checked itself
     * when the repository rebuilt it, so nothing else is checked here.
     *
     * @param snapshot the stored state
     * @return the settings
     */
    public static PlanningSettings restore(PlanningSettingsSnapshot snapshot) {
        return new PlanningSettings(snapshot);
    }

    /**
     * Changes the travel-time buckets (FR-321).
     *
     * @param buckets the new buckets
     */
    public void changeTravelBuckets(TravelBuckets buckets) {
        travelBuckets = Objects.requireNonNull(buckets, "buckets");
    }

    /**
     * Changes the travel-time buffer (FR-227).
     *
     * @param newBuffer the new buffer
     */
    public void changeBuffer(Buffer newBuffer) {
        buffer = Objects.requireNonNull(newBuffer, "newBuffer");
    }

    /**
     * Changes the default daily solve limit, which stays within 5 to 60 seconds (FR-183, NFR-06).
     *
     * @param limit the new default
     */
    public void changeDefaultSolveLimit(SolveLimit limit) {
        defaultSolveLimit = Objects.requireNonNull(limit, "limit");
    }

    /**
     * Turns the fixed vehicle cost on or off, or changes it (FR-21).
     *
     * @param cost the new cost
     */
    public void changeFixedVehicleCost(FixedVehicleCost cost) {
        fixedVehicleCost = Objects.requireNonNull(cost, "cost");
    }

    /**
     * Changes the departure allowance (FR-67).
     *
     * @param allowance the new allowance
     */
    public void changeDepartureAllowance(DepartureAllowance allowance) {
        departureAllowance = Objects.requireNonNull(allowance, "allowance");
    }

    /**
     * The settings' state, for the repository, for the use cases that read them, and for tests.
     *
     * @return a snapshot that {@link #restore} rebuilds
     */
    public PlanningSettingsSnapshot snapshot() {
        return new PlanningSettingsSnapshot(
                version(), travelBuckets, buffer, defaultSolveLimit, fixedVehicleCost, departureAllowance);
    }
}
