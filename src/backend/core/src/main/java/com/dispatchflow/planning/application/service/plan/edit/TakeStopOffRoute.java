package com.dispatchflow.planning.application.service.plan.edit;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Takes a stop of a ready plan off its route into the drops, with the reason <em>removed by
 * dispatcher</em>, an exception to BR-08 (FR-146, FR-236). It asks the validator about {@code
 * Plan.afterTakeOff} before its transaction (DL §6.3, ADR-112).
 *
 * <p>When the road network can't be reached for the evaluation's matrix, the assembler throws
 * {@code RoadNetworkUnavailable}, and the use case asks {@code FeasibilityChecker.unavailableFor}
 * for the plan's version. It then refuses with {@code ValidatorUnavailable}, as for an unreachable
 * optimizer. An edit checks no roster: BR-20 binds when the plan is published, where {@code
 * PublishPlan} checks it.
 *
 * <p>Before its transaction it reads the plan as the change would leave it through {@link
 * TakeStopOffRouteChange#layoutAfter}, builds the evaluation request and asks the validator; {@link
 * TakeStopOffRouteChange#apply} then holds the transaction, which loads the plan again, takes the
 * stop off as judged and writes the audit record; after it commits, the dispatchers' open screens
 * are told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M4.
 */
public final class TakeStopOffRoute {

    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final LiveUpdates liveUpdates;
    private final TakeStopOffRouteChange change;

    /**
     * Creates the use case.
     *
     * @param assembler builds the evaluation request
     * @param checker the optimizer's validator
     * @param liveUpdates the dispatchers' open screens
     * @param change the take-off's transaction
     */
    public TakeStopOffRoute(
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            LiveUpdates liveUpdates,
            TakeStopOffRouteChange change) {
        this.assembler = assembler;
        this.checker = checker;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Takes the stop off.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param stop the stop
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, {@code StaleEvaluation}, {@code ValidatorUnavailable}, {@code
     *     StopNotInPlan}, or {@code RouteLocked}
     * @throws com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest when the
     *     optimizer refused the evaluation request as invalid, a defect of the request the core
     *     built, which fails the call
     */
    public void takeOff(Actor actor, PlanId plan, StopId stop, Version expected) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
