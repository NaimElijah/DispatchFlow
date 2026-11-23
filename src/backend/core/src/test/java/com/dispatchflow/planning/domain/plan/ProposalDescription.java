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
import java.util.Optional;

/**
 * What a test sees of a {@link ReoptimizationProposal}, read through its getters: a value that
 * compares by every part. Tests compare a proposal before and after a refused decline, or after a
 * round trip through the database, through it.
 *
 * @param id the proposal's ID
 * @param version the version it was stored at, or empty when it was never saved
 * @param plan the plan it would change
 * @param job the job that computed it
 * @param basis the revision and fact time it was computed from
 * @param routes its routes
 * @param dropped its drops
 * @param excluded the vehicles it left out
 * @param withdrawn the vehicles it withdraws mid-route
 * @param objective its objective by term
 * @param lateStops the stops it keeps past their window end
 * @param withoutUsablePosition the vehicles with no usable position
 * @param driverChanges what changes for each driver
 * @param needsGoodsHandOver the orders whose goods need a hand-over
 * @param createdAt when it was created
 * @param state ready or declined
 */
public record ProposalDescription(
        ProposalId id,
        Optional<Version> version,
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

    /**
     * Describes a proposal.
     *
     * @param proposal the proposal
     * @return what its getters give
     */
    public static ProposalDescription of(ReoptimizationProposal proposal) {
        return new ProposalDescription(
                proposal.id(),
                proposal.savedVersion(),
                proposal.plan(),
                proposal.job(),
                proposal.basis(),
                proposal.routes(),
                proposal.dropped(),
                proposal.excluded(),
                proposal.withdrawn(),
                proposal.objective(),
                proposal.lateStops(),
                proposal.withoutUsablePosition(),
                proposal.driverChanges(),
                proposal.needsGoodsHandOver(),
                proposal.createdAt(),
                proposal.state());
    }

    /**
     * The same description at another stored version, as a test expects a proposal back after
     * saving it.
     *
     * @param stored the version it is expected at
     * @return the description with that version
     */
    public ProposalDescription at(Version stored) {
        return new ProposalDescription(
                id,
                Optional.of(stored),
                plan,
                job,
                basis,
                routes,
                dropped,
                excluded,
                withdrawn,
                objective,
                lateStops,
                withoutUsablePosition,
                driverChanges,
                needsGoodsHandOver,
                createdAt,
                state);
    }
}
