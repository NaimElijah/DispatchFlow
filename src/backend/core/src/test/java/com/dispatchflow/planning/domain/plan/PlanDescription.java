package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.planning.domain.reconciliation.LateOutcome;
import com.dispatchflow.planning.domain.solution.RoutePlan;
import com.dispatchflow.planning.domain.solution.StopPlan;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.domain.solution.Withdrawal;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Everything a {@link Plan} stores, as values read through its getters: what a test compares to
 * show a refused command changed nothing, and a plan as loaded to the plan as its last command left
 * it. Its routes and stops are values, so a route read before a command keeps its fields after it,
 * where the plan's own {@link Route} entity would have changed in place. The version is not part of
 * it: no command changes it, and a save moves it.
 *
 * @param id the plan
 * @param date its delivery date
 * @param openedAt when it was opened
 * @param status its state
 * @param applied the solve its routes came from
 * @param routes its routes, in the plan's order
 * @param dropped its drops, ordered by order and attempt
 * @param excluded the vehicles its last solve or accepted re-optimization left out
 * @param pins its pins, ordered by order
 * @param violations its open violations
 * @param undo the edits since the last solve, oldest first
 * @param redo the undone edits, oldest first
 */
public record PlanDescription(
        PlanId id,
        DeliveryDate date,
        Instant openedAt,
        Plan.Status status,
        Optional<AppliedSolve> applied,
        List<RouteDescription> routes,
        List<DroppedOrder> dropped,
        List<ExcludedVehicle> excluded,
        List<Pin> pins,
        List<Violation> violations,
        List<Edit> undo,
        List<Edit> redo) {

    /**
     * Describes a plan as it is.
     *
     * @param plan the plan
     * @return its description
     */
    public static PlanDescription of(Plan plan) {
        return new PlanDescription(
                plan.id(),
                plan.date(),
                plan.openedAt(),
                plan.status(),
                plan.applied(),
                plan.routes().stream().map(RouteDescription::of).toList(),
                plan.dropped(),
                plan.excluded(),
                plan.pins(),
                plan.violations(),
                plan.undo(),
                plan.redo());
    }

    /**
     * One route, with every field it stores.
     *
     * @param id the route
     * @param index its label and colour slot
     * @param vehicle its vehicle
     * @param driver its driver
     * @param lock its lock, if locked
     * @param check whether the validator judged it since it changed
     * @param withdrawal its withdrawal, if withdrawn
     * @param confirmedReturn the return a confirmed hand-over set, if one did
     * @param plan its times, if timed
     * @param stops its stops, in order
     */
    public record RouteDescription(
            RouteId id,
            RouteIndex index,
            VehicleId vehicle,
            AccountId driver,
            Optional<RouteLock> lock,
            CheckState check,
            Optional<Withdrawal> withdrawal,
            Optional<SolveTime> confirmedReturn,
            Optional<RoutePlan> plan,
            List<StopDescription> stops) {

        static RouteDescription of(Route route) {
            return new RouteDescription(
                    route.id(),
                    route.index(),
                    route.vehicle(),
                    route.driver(),
                    route.lock(),
                    route.check(),
                    route.withdrawal(),
                    route.confirmedReturn(),
                    route.plan(),
                    route.stops().stream().map(StopDescription::of).toList());
        }
    }

    /**
     * One stop, with every field it stores.
     *
     * @param id the stop
     * @param order its order
     * @param attempt its attempt
     * @param plan its planned times, if it has them
     * @param late how it joined its route after a late fact, if it did
     */
    public record StopDescription(
            StopId id, OrderId order, Attempt attempt, Optional<StopPlan> plan, Optional<LateOutcome> late) {

        static StopDescription of(PlannedStop stop) {
            return new StopDescription(stop.id(), stop.order(), stop.attempt(), stop.plan(), stop.late());
        }
    }
}
