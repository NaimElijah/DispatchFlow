package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.basis.ProposalBasis;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.planning.domain.solution.DriverChange;
import com.dispatchflow.planning.domain.solution.LateStop;
import com.dispatchflow.planning.domain.solution.ObjectiveTerms;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A {@link ReoptimizationProposal}'s whole state, which the repository stores, {@link
 * ReoptimizationProposal#restore} rebuilds and {@link Plan#accept} judges.
 *
 * @param id the proposal
 * @param version the version it was loaded at
 * @param plan the plan it would change
 * @param job the job that computed it
 * @param basis the revision and fact time it was computed from
 * @param routes its routes, keyed by vehicle and order, not yet by the plan's IDs
 * @param dropped its drops
 * @param excluded the vehicles it left out, each with its cause (FR-197, ADR-112)
 * @param withdrawn the vehicles it withdraws mid-route
 * @param objective its objective by term
 * @param lateStops the stops it keeps past their window end
 * @param withoutUsablePosition the vehicles with no usable position
 * @param driverChanges what changes for each driver
 * @param needsGoodsHandOver the orders whose goods need a hand-over
 * @param createdAt when it was created
 * @param state ready or declined
 */
public record ReoptimizationProposalSnapshot(
        ProposalId id,
        Version version,
        PlanId plan,
        JobId job,
        ProposalBasis basis,
        List<SolvedRoute> routes,
        List<DroppedOrder> dropped,
        List<ExcludedVehicle> excluded,
        List<VehicleWithdrawal> withdrawn,
        ObjectiveTerms objective,
        List<LateStop> lateStops,
        List<VehicleId> withoutUsablePosition,
        List<DriverChange> driverChanges,
        List<OrderId> needsGoodsHandOver,
        Instant createdAt,
        ReoptimizationProposal.State state) {

    /** Checks every part is present, and copies the lists. */
    public ReoptimizationProposalSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(basis, "basis");
        routes = List.copyOf(routes);
        dropped = List.copyOf(dropped);
        excluded = List.copyOf(excluded);
        withdrawn = List.copyOf(withdrawn);
        Objects.requireNonNull(objective, "objective");
        lateStops = List.copyOf(lateStops);
        withoutUsablePosition = List.copyOf(withoutUsablePosition);
        driverChanges = List.copyOf(driverChanges);
        needsGoodsHandOver = List.copyOf(needsGoodsHandOver);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(state, "state");
    }

    /**
     * Whether the proposal withdraws a vehicle.
     *
     * @param vehicle the vehicle
     * @return {@code true} when it is among the withdrawn
     */
    public boolean withdraws(VehicleId vehicle) {
        return withdrawn.stream().anyMatch(withdrawal -> withdrawal.vehicle().equals(vehicle));
    }
}
