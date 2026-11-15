package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.reconciliation.LateOutcome;
import com.dispatchflow.planning.domain.solution.StopPlan;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import java.util.Optional;

/**
 * An order's place on a route, inside {@link Plan} (DL §4.4, §17 "Stop"). Its ID stays the same
 * while it moves between routes (DL §3.1). A stop that joined its route through a late fact may have
 * no planned times.
 */
record PlannedStop(StopId id, OrderId order, Attempt attempt, Optional<StopPlan> plan, Optional<LateOutcome> late) {

    static PlannedStop from(PlanSnapshot.StopSnapshot snapshot) {
        return new PlannedStop(snapshot.id(), snapshot.order(), snapshot.attempt(), snapshot.plan(), snapshot.late());
    }

    StopKey key() {
        return new StopKey(order, attempt);
    }

    PlannedStop withPlan(Optional<StopPlan> newPlan) {
        return new PlannedStop(id, order, attempt, newPlan, late);
    }

    PlanSnapshot.StopSnapshot snapshot() {
        return new PlanSnapshot.StopSnapshot(id, order, attempt, plan, late);
    }
}
