package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.planning.domain.plan.Pin;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;

/**
 * One plan as the dispatcher's planning workspace shows it, on the map, the timeline and the table
 * with one shared selection (FR-24, FR-25, FR-141, FR-143, FR-148 to FR-151, FR-223, FR-232, FR-241):
 * its state, its routes and stops, its dropped orders with their reasons (FR-18, FR-144, FR-223),
 * the vehicles its solve left out (FR-197), its pins (FR-91), its open violations (FR-233), and how
 * many edits undo and redo hold (FR-92, FR-145).
 *
 * <p>It holds Planning's data only; the screen joins the orders' windows, demands and locations and
 * the fleet's capacities, depots and shifts from those modules' queries (FR-143, FR-149).
 *
 * <p>To Be Implemented.
 *
 * @param plan the plan
 * @param date its delivery date
 * @param version its version, which the screen's next change sends as {@code If-Match} (BR-76)
 * @param status its state, with the job it waits for while solving (FR-16)
 * @param routes its routes, in the plan's order, withdrawn ones included
 * @param dropped its dropped orders, each with its reason and the fact behind it (FR-144)
 * @param excluded the vehicles its last solve left out, each with its cause (FR-197)
 * @param pins its pins (FR-91)
 * @param violations its open violations, each naming its route or order (FR-233, FR-241)
 * @param undoable how many edits undo holds, so solving again warns before discarding them (FR-92)
 * @param redoable how many undone edits redo holds
 */
public record PlanWorkspaceView(
        PlanId plan,
        DeliveryDate date,
        Version version,
        Plan.Status status,
        List<RouteView> routes,
        List<DroppedOrder> dropped,
        List<ExcludedVehicle> excluded,
        List<Pin> pins,
        List<Violation> violations,
        int undoable,
        int redoable) {

    /** Checks every part is present, and copies the lists. */
    public PlanWorkspaceView {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(status, "status");
        routes = List.copyOf(routes);
        dropped = List.copyOf(dropped);
        excluded = List.copyOf(excluded);
        pins = List.copyOf(pins);
        violations = List.copyOf(violations);
    }
}
