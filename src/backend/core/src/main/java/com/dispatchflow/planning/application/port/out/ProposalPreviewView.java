package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.basis.ProposalBasis;
import com.dispatchflow.planning.domain.plan.ReoptimizationProposal;
import com.dispatchflow.planning.domain.solution.DriverChange;
import com.dispatchflow.planning.domain.solution.DroppedOrder;
import com.dispatchflow.planning.domain.solution.LateStop;
import com.dispatchflow.planning.domain.solution.ObjectiveTerms;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A re-optimization's preview, before a dispatcher accepts or declines it; nothing changes for the
 * drivers until then (FR-36, FR-103, FR-255, FR-334, BR-81). Its lists keep IDs and durations only,
 * until the optimizer's re-optimization report settles their shape in M9 (ADR-101 point 41).
 *
 * <p>Built in M9.
 *
 * @param proposal the proposal
 * @param version the proposal's version, which a decline sends as {@code If-Match} (BR-76)
 * @param plan the plan it would change
 * @param planVersion the plan's version, which an accept sends in its request body, because
 *     accepting changes the plan while the request's target is the proposal (BR-76)
 * @param job the job that computed it
 * @param basis the revision and fact time it was computed from (BR-25, BR-82)
 * @param createdAt when it was created
 * @param state ready, or declined by whom and when (BR-26)
 * @param driverChanges what changes for each driver (FR-36)
 * @param dropped the orders it would drop (FR-103)
 * @param lateStops the stops it keeps past their window end, and by how much (FR-103, BR-107)
 * @param withdrawn the vehicles it withdraws mid-route, each with its cause (FR-267)
 * @param withoutUsablePosition the vehicles with no usable position (BR-81)
 * @param needsGoodsHandOver the moved stops' and new orders' goods that need a hand-over (FR-334)
 * @param objective its objective, term by term
 * @param current the plan's totals now (FR-103)
 * @param proposed the plan's totals once it is accepted (FR-103)
 */
public record ProposalPreviewView(
        ProposalId proposal,
        Version version,
        PlanId plan,
        Version planVersion,
        JobId job,
        ProposalBasis basis,
        Instant createdAt,
        ReoptimizationProposal.State state,
        List<DriverChange> driverChanges,
        List<DroppedOrder> dropped,
        List<LateStop> lateStops,
        List<VehicleWithdrawal> withdrawn,
        List<VehicleId> withoutUsablePosition,
        List<OrderId> needsGoodsHandOver,
        ObjectiveTerms objective,
        Totals current,
        Totals proposed) {

    /** Checks every part is present, and copies the lists. */
    public ProposalPreviewView {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(planVersion, "planVersion");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(state, "state");
        driverChanges = List.copyOf(driverChanges);
        dropped = List.copyOf(dropped);
        lateStops = List.copyOf(lateStops);
        withdrawn = List.copyOf(withdrawn);
        withoutUsablePosition = List.copyOf(withoutUsablePosition);
        needsGoodsHandOver = List.copyOf(needsGoodsHandOver);
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(proposed, "proposed");
    }

    /**
     * A plan's totals, as the preview compares them (FR-103, FR-38).
     *
     * <p>Built in M9.
     *
     * @param metres the planned road distance
     * @param driving the planned driving time
     * @param stops the stops on routes
     * @param dropped the dropped orders
     */
    public record Totals(long metres, Duration driving, int stops, int dropped) {

        /** Checks the driving time is present. */
        public Totals {
            Objects.requireNonNull(driving, "driving");
        }
    }
}
