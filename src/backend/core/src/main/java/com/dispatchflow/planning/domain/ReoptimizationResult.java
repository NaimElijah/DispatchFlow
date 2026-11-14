package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What a succeeded re-optimization hands its job: the validated solution under the re-optimization
 * conventions (ADR-050), and the preview's facts (FR-36, FR-103, FR-334, BR-81). The worker builds it
 * from the optimizer's answer and the facts it gathered. Its preview lists keep IDs and durations
 * only, until PR 11's re-optimization report settles their shape (ADR-101).
 *
 * @param solution the validated routes and drops
 * @param factsAt when the facts it was computed from were gathered (BR-25)
 * @param withdrawn the vehicles it withdraws mid-route (FR-267)
 * @param objective its objective, term by term (FR-103)
 * @param lateStops the stops it keeps past their window end (BR-107)
 * @param withoutUsablePosition the vehicles with no usable position (BR-81)
 * @param driverChanges what changes for each driver (FR-36)
 * @param needsGoodsHandOver the moved stops and new orders whose goods need a hand-over (FR-334)
 */
public record ReoptimizationResult(
        ValidatedSolution solution,
        Instant factsAt,
        List<VehicleWithdrawal> withdrawn,
        ObjectiveTerms objective,
        List<LateStop> lateStops,
        List<VehicleId> withoutUsablePosition,
        List<DriverChange> driverChanges,
        List<OrderId> needsGoodsHandOver) {

    /** Checks every part is present, and copies the lists. */
    public ReoptimizationResult {
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(factsAt, "factsAt");
        withdrawn = List.copyOf(withdrawn);
        Objects.requireNonNull(objective, "objective");
        lateStops = List.copyOf(lateStops);
        withoutUsablePosition = List.copyOf(withoutUsablePosition);
        driverChanges = List.copyOf(driverChanges);
        needsGoodsHandOver = List.copyOf(needsGoodsHandOver);
    }
}
