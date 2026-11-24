package com.dispatchflow.planning.application.service.plan.edit;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Moves a stop of a ready plan to another position in its route or to another route; no
 * optimization runs, and the validator re-times the plan and flags every violation (FR-22, FR-23,
 * FR-196, FR-233). It asks the validator about {@code Plan.afterMove} before its transaction (DL
 * §6.3, ADR-112).
 *
 * <p>When the road network can't be reached for the evaluation's matrix, the assembler throws
 * {@code RoadNetworkUnavailable}, and the use case asks {@code FeasibilityChecker.unavailableFor}
 * for the plan's version. It then refuses with {@code ValidatorUnavailable}, as for an unreachable
 * optimizer. An edit checks no roster: BR-20 binds when the plan is published, where {@code
 * PublishPlan} checks it.
 *
 * <p>Before its transaction it reads the plan as the change would leave it through {@link
 * MoveStopChange#layoutAfter}, builds the evaluation request and asks the validator; {@link
 * MoveStopChange#apply} then holds the transaction, which loads the plan again, moves the stop as
 * judged and writes the audit record; after it commits, the dispatchers' open screens are told
 * (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M4.
 */
public final class MoveStop {

    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final LiveUpdates liveUpdates;
    private final MoveStopChange change;

    /**
     * Creates the use case.
     *
     * @param assembler builds the evaluation request
     * @param checker the optimizer's validator
     * @param liveUpdates the dispatchers' open screens
     * @param change the move's transaction
     */
    public MoveStop(
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            LiveUpdates liveUpdates,
            MoveStopChange change) {
        this.assembler = assembler;
        this.checker = checker;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Moves the stop.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param stop the stop
     * @param toRoute the route it goes to
     * @param position its position there, from 0, counted in the route without it
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, {@code StaleEvaluation}, {@code ValidatorUnavailable}, {@code
     *     StopNotInPlan}, {@code RouteNotInPlan}, {@code PositionOutOfRange}, or {@code
     *     RouteLocked} for either route (FR-147)
     * @throws com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest when the
     *     optimizer refused the evaluation request as invalid, a defect of the request the core
     *     built, which fails the call
     */
    public void move(Actor actor, PlanId plan, StopId stop, RouteId toRoute, int position, Version expected) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
