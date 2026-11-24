package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Hands a route of a published plan to another driver; its stops and times stay, no optimization
 * runs, and its reported stops keep their deliveries (FR-55, FR-191, FR-192, FR-302 to FR-304,
 * FR-394, FR-395).
 *
 * <p>In its transaction, {@link HandOverRouteChange#apply} asks Fleet's {@code
 * FleetFacts.assignability} whether the new driver can take the route's vehicle that date: {@code
 * DayRoster.checkAssignable}, given the standing Fleet combines from Access's Driver role and the
 * driver record (DL §4.2, ADR-062). So Fleet's listener on {@code RouteHandedOver} doesn't refuse
 * after Planning has committed. The answer becomes the domain's {@code DriverStanding}: {@code
 * AssignabilityFact.Assignable} an active, available driver; {@code NOT_AN_ACTIVE_DRIVER} one not
 * active, which {@code Plan.handOver} refuses as {@code NotAnActiveDriver}; {@code
 * DRIVER_NOT_ROSTERED} and {@code DRIVER_MARKED_UNAVAILABLE} one not available, refused as {@code
 * DriverNotAvailable}. Planning's domain has no error for {@code DRIVER_ALREADY_HAS_VEHICLE}, a
 * driver rostered to another vehicle whose route is not in the plan; re-optimization adds it to
 * {@code DriverStanding} and {@code Plan.handOver}'s refusals.
 *
 * <p>Before its transaction it asks the validator about {@code Plan.afterHandOver}, whose layout
 * {@link HandOverRouteChange#layoutAfter} reads. The assembler rosters the route's vehicle to the new
 * driver, with the new driver's shift on the date's roster, which is the shift the assignability
 * answer gives, so the validator judges BR-06 by the new driver's shift (ADR-112). This roster check,
 * through {@code DriverStanding}, is the only one: the evaluation sends the new driver whatever their
 * availability, and never refuses a problem for BR-20. An unreachable optimizer doesn't stop the
 * hand-over, and neither does an unreachable road network, for which the assembler throws {@code
 * RoadNetworkUnavailable} and the use case asks {@code FeasibilityChecker.unavailableFor} for the
 * plan's version: the route shows <em>not yet checked</em> until the re-check job runs (BR-70,
 * FR-395, ADR-058). It raises {@code PlanRevised}, then {@code RouteHandedOver}, so the roster
 * follows (FR-191).
 *
 * <p>{@link HandOverRouteChange#apply} holds the transaction, which loads the plan again, hands the
 * route over with the evaluation and writes the audit record; after it commits, the dispatchers' open
 * screens are told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>To Be Implemented.
 */
public final class HandOverRoute {

    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final LiveUpdates liveUpdates;
    private final HandOverRouteChange change;

    /**
     * Creates the use case.
     *
     * @param assembler builds the evaluation request
     * @param checker the optimizer's validator
     * @param liveUpdates the dispatchers' open screens
     * @param change the hand-over's read and transaction
     */
    public HandOverRoute(
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            LiveUpdates liveUpdates,
            HandOverRouteChange change) {
        this.assembler = assembler;
        this.checker = checker;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Hands the route over.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher, recorded as who handed it over
     * @param plan the plan
     * @param route the route
     * @param toDriver the new driver's account
     * @param breakConfirmed whether the dispatcher confirmed a hand-over that breaks the new
     *     driver's shift (FR-304)
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID, or no account has {@code toDriver}
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished},
     *     {@code DateInPast}, {@code RouteNotInPlan}, {@code RouteWithdrawn}, {@code
     *     DriverAlreadyHasRoute} (FR-302), {@code StaleEvaluation}, {@code NotAnActiveDriver},
     *     {@code DriverNotAvailable}, or {@code BreakNotConfirmed} (FR-304)
     * @throws com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest when the
     *     optimizer refused the evaluation request as invalid, a defect of the request the core
     *     built, which fails the call
     */
    public void handOver(
            Actor actor, PlanId plan, RouteId route, AccountId toDriver, boolean breakConfirmed, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
