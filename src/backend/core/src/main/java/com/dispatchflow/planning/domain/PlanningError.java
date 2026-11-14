package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Optional;

/**
 * Every refusal of the Planning context (DL §3.8, §4.4). The fields become the response's
 * parameters, so they hold IDs, dates, counts and Planning's own values, never text or personal
 * data. The rules no document settled, and the names they needed, are ADR-101's.
 */
public sealed interface PlanningError extends DomainError {

    /**
     * The date already has an uncancelled plan (BR-34).
     *
     * @param date the date
     * @param existing the plan it has
     */
    record PlanAlreadyExists(DeliveryDate date, PlanId existing) implements PlanningError {}

    /**
     * The plan's date is before today, so it is not solved, changed or published (BR-118).
     *
     * @param date the plan's date
     */
    record DateInPast(DeliveryDate date) implements PlanningError {}

    /**
     * The midnight after the plan's date has not come yet (BR-102, BR-127).
     *
     * @param date the plan's date
     */
    record DateNotOver(DeliveryDate date) implements PlanningError {}

    /**
     * The command needs a plan in draft or ready (BR-12, BR-60, FR-91).
     *
     * @param plan the plan
     */
    record PlanNotDraftOrReady(PlanId plan) implements PlanningError {}

    /**
     * The command needs a ready plan (BR-12, BR-61, FR-22).
     *
     * @param plan the plan
     */
    record PlanNotReady(PlanId plan) implements PlanningError {}

    /**
     * The command needs a published plan (BR-12, FR-35, FR-55, FR-336).
     *
     * @param plan the plan
     */
    record PlanNotPublished(PlanId plan) implements PlanningError {}

    /**
     * Only an unpublished plan, in draft, solving or ready, is abandoned (BR-127, ADR-101).
     *
     * @param plan the plan
     */
    record PlanNotDraftSolvingOrReady(PlanId plan) implements PlanningError {}

    /**
     * An order and attempt would be served or dropped more than once (BR-07).
     *
     * @param order the order
     * @param attempt its attempt
     */
    record OrderAlreadyInPlan(OrderId order, Attempt attempt) implements PlanningError {}

    /**
     * A vehicle would drive two routes (BR-39); a withdrawn route doesn't count.
     *
     * @param vehicle the vehicle
     * @param route the route it already drives
     */
    record VehicleAlreadyHasRoute(VehicleId vehicle, RouteId route) implements PlanningError {}

    /**
     * A driver would drive two routes (BR-39, FR-302); a withdrawn route doesn't count.
     *
     * @param driver the driver's account
     * @param route the route they already drive
     */
    record DriverAlreadyHasRoute(AccountId driver, RouteId route) implements PlanningError {}

    /**
     * An evaluation was read for another version of the plan, or doesn't describe the plan after
     * the change (FR-23, FR-233, ADR-101).
     *
     * @param evaluated the version it was read for
     * @param current the plan's version
     */
    record StaleEvaluation(Version evaluated, Version current) implements PlanningError {}

    /** An edit can't be judged, because the optimizer could not be reached (ADR-101). */
    record ValidatorUnavailable() implements PlanningError {}

    /**
     * The route is locked against edits and solves (FR-147).
     *
     * @param route the route
     */
    record RouteLocked(RouteId route) implements PlanningError {}

    /**
     * No route of the plan holds the stop (ADR-101).
     *
     * @param stop the stop
     */
    record StopNotInPlan(StopId stop) implements PlanningError {}

    /**
     * The plan has no such route (ADR-101).
     *
     * @param route the route
     */
    record RouteNotInPlan(RouteId route) implements PlanningError {}

    /**
     * A position past the route's stops (ADR-101).
     *
     * @param route the route
     * @param position the refused position, from 0
     */
    record PositionOutOfRange(RouteId route, int position) implements PlanningError {}

    /**
     * Only a dropped order can be placed by hand (FR-235, ADR-101).
     *
     * @param order the order
     * @param attempt its attempt
     */
    record OrderNotDropped(OrderId order, Attempt attempt) implements PlanningError {}

    /**
     * The order was dropped for a missing or unreachable location, so it can't be placed until it
     * is solved again (FR-237, ADR-101).
     *
     * @param order the order
     */
    record OrderNotRoutable(OrderId order) implements PlanningError {}

    /**
     * The order has no pin to remove (ADR-101).
     *
     * @param order the order
     */
    record OrderNotPinned(OrderId order) implements PlanningError {}

    /**
     * The route has no lock to remove (ADR-101).
     *
     * @param route the route
     */
    record RouteNotLocked(RouteId route) implements PlanningError {}

    /** No edit since the last solve is left to undo (FR-145, ADR-101). */
    record NothingToUndo() implements PlanningError {}

    /** No undone edit is left to redo (FR-145, ADR-101). */
    record NothingToRedo() implements PlanningError {}

    /**
     * Publishing stopped at a step, whose every refusal is listed with it (BR-72).
     *
     * @param step the step that refused
     */
    record PublishingRefused(PublishingStep step) implements PlanningError {}

    /**
     * A route's vehicle is unavailable, or its driver is not the vehicle's driver with the Driver
     * role (BR-20, BR-63).
     *
     * @param route the route
     * @param vehicle its vehicle, which changed
     */
    record RouteNotStaffed(RouteId route, VehicleId vehicle) implements PlanningError {}

    /**
     * An uncancelled order of the date is neither served nor dropped (BR-22).
     *
     * @param order the order
     * @param attempt its current attempt
     */
    record OrderNotAccounted(OrderId order, Attempt attempt) implements PlanningError {}

    /**
     * An order was placed after the solve was requested, or changed while the plan was solving, or
     * left the date since (BR-71, FR-317).
     *
     * @param order the order
     */
    record OrderChangedSinceSolve(OrderId order) implements PlanningError {}

    /**
     * A flagged violation is still open (BR-17).
     *
     * @param rule the broken rule
     * @param route the route it concerns, if one
     * @param order the order it concerns, if one
     */
    record OpenViolation(RuleCode rule, Optional<RouteId> route, Optional<OrderId> order) implements PlanningError {}

    /**
     * The re-check at publishing found a broken rule (BR-21, BR-67).
     *
     * @param rule the broken rule
     * @param route the route it concerns, if one
     * @param order the order it concerns, if one
     */
    record RecheckFailed(RuleCode rule, Optional<RouteId> route, Optional<OrderId> order) implements PlanningError {}

    /** The re-check at publishing could not run (BR-70, ADR-058). */
    record RecheckUnavailable() implements PlanningError {}

    /**
     * The proposal belongs to another plan (DL §4.4).
     *
     * @param proposal the proposal
     * @param plan the plan it belongs to
     */
    record ProposalForAnotherPlan(ProposalId proposal, PlanId plan) implements PlanningError {}

    /**
     * The proposal was declined (DL §4.4).
     *
     * @param proposal the proposal
     */
    record ProposalDeclined(ProposalId proposal) implements PlanningError {}

    /**
     * Something the proposal moves, drops or relies on changed since its basis (BR-25, BR-82).
     *
     * @param proposal the proposal
     * @param changes every change found, each naming what changed
     */
    record ProposalOutdated(ProposalId proposal, List<BasisChange> changes) implements PlanningError {

        /** Copies the changes. */
        public ProposalOutdated {
            changes = List.copyOf(changes);
        }
    }

    /**
     * Vehicles the proposal keeps driving no longer meet BR-20 (BR-64, BR-65).
     *
     * @param vehicles every such vehicle
     */
    record VehicleUnavailable(List<VehicleId> vehicles) implements PlanningError {

        /** Copies the vehicles. */
        public VehicleUnavailable {
            vehicles = List.copyOf(vehicles);
        }
    }

    /**
     * A re-optimization's solution gives one vehicle two routes, withdrawn or not, so it can't become
     * a proposal (BR-39, ADR-101).
     *
     * @param vehicle the vehicle
     */
    record VehicleRoutedTwice(VehicleId vehicle) implements PlanningError {}

    /**
     * A re-optimization's solution puts one driver on two routes it doesn't withdraw, so it can't
     * become a proposal (BR-39, ADR-101).
     *
     * @param driver the driver's account
     */
    record DriverRoutedTwice(AccountId driver) implements PlanningError {}

    /** Every route index from 1 to 30 is taken (ADR-101). */
    record RouteIndexesUsedUp() implements PlanningError {}

    /**
     * The account is not active, or doesn't hold the Driver role (FR-302).
     *
     * @param driver the account
     */
    record NotAnActiveDriver(AccountId driver) implements PlanningError {}

    /**
     * The driver is marked unavailable on the date (FR-302).
     *
     * @param driver the driver's account
     */
    record DriverNotAvailable(AccountId driver) implements PlanningError {}

    /**
     * The hand-over breaks the new driver's shift, and the dispatcher hasn't confirmed it (FR-304).
     *
     * @param route the route
     * @param driver the new driver
     */
    record BreakNotConfirmed(RouteId route, AccountId driver) implements PlanningError {}

    /**
     * A route withdrawn mid-route is not handed over (FR-268, ADR-101).
     *
     * @param route the route
     */
    record RouteWithdrawn(RouteId route) implements PlanningError {}

    /**
     * Routes of the plan have started, so it can't be cancelled (FR-336).
     *
     * @param routes every started route
     */
    record RouteAlreadyStarted(List<RouteId> routes) implements PlanningError {

        /** Copies the routes. */
        public RouteAlreadyStarted {
            routes = List.copyOf(routes);
        }
    }

    /**
     * Stops of the plan have reports, so it can't be cancelled (FR-336, ADR-101).
     *
     * @param stops every reported stop
     */
    record StopAlreadyReported(List<StopId> stops) implements PlanningError {

        /** Copies the stops. */
        public StopAlreadyReported {
            stops = List.copyOf(stops);
        }
    }

    /**
     * The lane already holds its limit of queued jobs (BR-112).
     *
     * @param lane the lane
     * @param limit the limit
     */
    record SolveQueueFull(Lane lane, int limit) implements PlanningError {}

    /**
     * The lane capacity given was read for another lane (ADR-101).
     *
     * @param expected the lane the job goes to
     * @param given the lane the capacity was read for
     */
    record CapacityForAnotherLane(Lane expected, Lane given) implements PlanningError {}

    /**
     * The plan already has a queued or started re-optimization (BR-38).
     *
     * @param plan the plan
     */
    record ReoptimizationAlreadyRunning(PlanId plan) implements PlanningError {}

    /**
     * The job has succeeded or failed, and never changes again (DL §4.4, §11).
     *
     * @param job the job
     */
    record JobAlreadyEnded(JobId job) implements PlanningError {}

    /**
     * Only a queued job is claimed (DL §9.2, ADR-101).
     *
     * @param job the job
     */
    record JobNotQueued(JobId job) implements PlanningError {}

    /**
     * A search starts only once the job is building its matrix (DL §9.2, ADR-101).
     *
     * @param job the job
     */
    record JobNotBuildingMatrix(JobId job) implements PlanningError {}

    /**
     * Only a running job succeeds (DL §9.2, ADR-101).
     *
     * @param job the job
     */
    record JobNotRunning(JobId job) implements PlanningError {}

    /**
     * The result is of the other lane's kind (ADR-101).
     *
     * @param job the job
     * @param lane the job's lane
     */
    record ResultForAnotherLane(JobId job, Lane lane) implements PlanningError {}

    /**
     * The proposal was already declined (DL §4.4).
     *
     * @param proposal the proposal
     */
    record ProposalAlreadyDeclined(ProposalId proposal) implements PlanningError {}

    /**
     * The validator rejected the solution, so it is not a validated one (BR-15, BR-57).
     *
     * @param violations how many violations it found
     */
    record SolutionRejected(int violations) implements PlanningError {}

    /**
     * An attempt number below 1 (FR-258).
     *
     * @param attempt the refused number
     */
    record InvalidAttempt(int attempt) implements PlanningError {}

    /** A rule code that is not a requirement row's ID, such as {@code BR-03} (ADR-101). */
    record MalformedRuleCode() implements PlanningError {}

    /** A solver name that is not a lower-case name of at most 40 characters (FR-224, ADR-101). */
    record InvalidSolverName() implements PlanningError {}

    /**
     * A daily solve limit outside 5 to 60 seconds, or not whole seconds (NFR-06, FR-19).
     *
     * @param seconds the refused limit, in whole seconds
     */
    record SolveLimitOutOfRange(long seconds) implements PlanningError {}

    /** A stop's times out of order, or on different dates (ADR-101). */
    record InvalidStopTimes() implements PlanningError {}

    /** A travel leg with negative seconds or metres (ADR-101). */
    record InvalidTravelLeg() implements PlanningError {}

    /** Travel buckets whose hours are empty, overlap or are out of order (FR-321, FR-408). */
    record InvalidTravelBuckets() implements PlanningError {}

    /** A travel-time factor of zero or less (FR-321). */
    record NonPositiveTravelFactor() implements PlanningError {}

    /** A travel-time buffer below zero (FR-227). */
    record NegativeBuffer() implements PlanningError {}

    /** A fixed vehicle cost, when on, of zero or less (FR-21). */
    record NonPositiveFixedVehicleCost() implements PlanningError {}

    /** A departure allowance below zero (FR-67). */
    record NegativeDepartureAllowance() implements PlanningError {}
}
