package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.baseline.BaselineComparison;
import com.dispatchflow.shared.PlanId;
import java.util.Objects;
import java.util.Optional;

/**
 * A plan set against its nearest-neighbour baseline (FR-39, FR-266, FR-269, FR-270): on
 * kilometres, driver-hours and planned on-time; on kilometres and planned on-time only, naming the
 * withdrawn vehicles, once a vehicle was withdrawn mid-route, when the baseline was solved after
 * the re-optimization and is not strictly like for like (FR-270); or not compared for a cancelled
 * plan (FR-338). Both planned on-time rates leave out the orders cancelled now (BR-30, ADR-109).
 *
 * <p>Built in M5.
 *
 * @param plan the plan
 * @param comparison the comparison, or empty while the baseline is not computed yet, between the
 *     publication and the listener's solve
 */
public record BaselineView(PlanId plan, Optional<BaselineComparison> comparison) {

    /** Checks both parts are present, possibly empty. */
    public BaselineView {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(comparison, "comparison");
    }
}
