package com.dispatchflow.planning.application.service.plan.edit;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Puts a dropped order of a ready plan on a route by hand, as a new stop, with the same re-timing
 * and flags as a move (FR-235, FR-237, FR-23, FR-233). It draws the new stop's ID first, and asks
 * the validator about {@code Plan.afterPlacing} with it before its transaction (DL §6.3, ADR-112).
 * The order's attempt is the one the plan drops.
 *
 * <p>When the road network can't be reached for the evaluation's matrix, the assembler throws
 * {@code RoadNetworkUnavailable}, and the use case asks {@code FeasibilityChecker.unavailableFor}
 * for the plan's version. It then refuses with {@code ValidatorUnavailable}, as for an unreachable
 * optimizer. An edit checks no roster: BR-20 binds when the plan is published, where {@code
 * PublishPlan} checks it.
 *
 * <p>Before its transaction it draws the stop's ID, reads the plan as the change would leave it
 * through {@link PlaceDroppedOrderChange#layoutAfter}, builds the evaluation request and asks the
 * validator; {@link PlaceDroppedOrderChange#apply} then holds the transaction, which loads the plan
 * again, places the order under that ID as judged and writes the audit record; after it commits,
 * the dispatchers' open screens are told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>To Be Implemented.
 */
public final class PlaceDroppedOrder {

    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final IdGenerator ids;
    private final LiveUpdates liveUpdates;
    private final PlaceDroppedOrderChange change;

    /**
     * Creates the use case.
     *
     * @param assembler builds the evaluation request
     * @param checker the optimizer's validator
     * @param ids the new stop's ID
     * @param liveUpdates the dispatchers' open screens
     * @param change the placing's transaction
     */
    public PlaceDroppedOrder(
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            IdGenerator ids,
            LiveUpdates liveUpdates,
            PlaceDroppedOrderChange change) {
        this.assembler = assembler;
        this.checker = checker;
        this.ids = ids;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Places the order.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param order the dropped order
     * @param toRoute the route
     * @param position its position there, from 0
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, {@code StaleEvaluation}, {@code ValidatorUnavailable}, {@code
     *     OrderNotDropped}, {@code OrderNotRoutable} for a missing or unreachable location
     *     (FR-237), {@code RouteNotInPlan}, {@code PositionOutOfRange}, or {@code RouteLocked}
     * @throws com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest when the
     *     optimizer refused the evaluation request as invalid, a defect of the request the core
     *     built, which fails the call
     */
    public void place(Actor actor, PlanId plan, OrderId order, RouteId toRoute, int position, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
