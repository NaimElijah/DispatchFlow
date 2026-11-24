package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Publishes a ready plan as revision 1, so each driver sees their own route (FR-26, FR-234, FR-317,
 * BR-17, BR-20 to BR-22, BR-63, BR-67 to BR-72). Before its transaction it reads the plan as it is
 * through {@link PublishPlanChange#currentLayout} and asks the optimizer to re-check it (BR-21). In
 * its transaction, {@link PublishPlanChange#apply} reads the other facts {@code PublishingChecks}
 * holds: each route's roster answer, from Fleet's roster and Access's Driver role (BR-20), and the
 * date's uncancelled orders (BR-22, BR-71). {@code Plan.publish} then runs BR-72's four steps in
 * order. It raises {@code PlanPublished}.
 *
 * <p>The roster check runs here, not in the evaluation: each route's {@code RouteStaffing} answer
 * in {@code PublishingChecks}, built from Fleet's {@code FleetFacts.rosterOn} and Access's Driver
 * role, is BR-72's first step (BR-20, BR-63). The re-check's request sends every routed vehicle
 * with the route's driver, whatever their availability, so the evaluation never refuses a problem
 * for it.
 *
 * <p>When the road network can't be reached for the re-check's matrix, the assembler throws {@code
 * RoadNetworkUnavailable}, and the use case asks {@code FeasibilityChecker.unavailableFor} for the
 * plan's version. Publishing then refuses with {@code RecheckUnavailable}, as for an unreachable
 * optimizer (BR-70).
 *
 * <p>{@link PublishPlanChange#apply} holds the transaction, which loads the plan again, publishes it
 * with the re-check and writes the audit record; after it commits, the dispatchers' open screens are
 * told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M5.
 */
public final class PublishPlan {

    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final LiveUpdates liveUpdates;
    private final PublishPlanChange change;

    /**
     * Creates the use case.
     *
     * @param assembler builds the re-check's evaluation request
     * @param checker the optimizer's validator
     * @param liveUpdates the dispatchers' open screens
     * @param change the publishing's read and transaction
     */
    public PublishPlan(
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            LiveUpdates liveUpdates,
            PublishPlanChange change) {
        this.assembler = assembler;
        this.checker = checker;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Publishes the plan.
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher, recorded as who published it
     * @param plan the plan
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast} (BR-118), or {@code StaleEvaluation} for a re-check of another version or
     *     layout
     * @throws com.dispatchflow.shared.RuleViolations with Planning's {@code PublishingRefused} and
     *     the first refusing step's refusals: {@code RouteNotStaffed} (BR-20, BR-63); every {@code
     *     OrderNotAccounted}, then every {@code OrderChangedSinceSolve} (BR-22, BR-71, FR-317);
     *     {@code OpenViolation} (BR-17); {@code RecheckUnavailable} or {@code RecheckFailed}
     *     (BR-21, BR-67, BR-70)
     * @throws com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest when the
     *     optimizer refused the re-check's request as invalid, a defect of the request the core
     *     built, which fails the call
     */
    public void publish(Actor actor, PlanId plan, Version expected) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
