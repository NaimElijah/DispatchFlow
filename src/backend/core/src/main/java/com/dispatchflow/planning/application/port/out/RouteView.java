package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.plan.CheckState;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.plan.RouteIndex;
import com.dispatchflow.planning.domain.plan.RouteLock;
import com.dispatchflow.planning.domain.reconciliation.LateOutcome;
import com.dispatchflow.planning.domain.solution.RoutePlan;
import com.dispatchflow.planning.domain.solution.StopPlan;
import com.dispatchflow.planning.domain.solution.Withdrawal;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One route of a plan as the planning screens show it: its label and colour slot, its vehicle and
 * driver, its lock, its check state, and its stops with their planned times and legs (FR-24, FR-25,
 * FR-143, FR-148, FR-151, FR-232, FR-241, FR-395). The total distance and waiting time follow from
 * the legs and times (FR-232).
 *
 * <p>It holds Planning's data only. A stop's window, demand, door flag and location are Customers
 * &amp; Orders', and a vehicle's capacity and a driver's shift are Fleet's: the screen reads them
 * through those modules' queries, as {@code planning.application.api} says.
 *
 * <p>Built in M0.
 *
 * @param plan the plan that holds the route
 * @param date the plan's delivery date
 * @param planStatus the plan's state, which decides who may see the route (BR-41)
 * @param route the route
 * @param index its stable index from 1 to 30, its label and colour slot (FR-148, FR-240)
 * @param vehicle its vehicle
 * @param driver its driver
 * @param lock its lock, if it has one (FR-147)
 * @param check whether the validator checked it since its last change (FR-395)
 * @param withdrawal why it was withdrawn mid-route, if it was (FR-267)
 * @param times when it leaves and returns, and its last leg, when the validator timed it
 * @param confirmedReturn the return time a confirmed hand-over set, if one did (FR-394)
 * @param stops its stops, in planned order
 */
public record RouteView(
        PlanId plan,
        DeliveryDate date,
        Plan.Status planStatus,
        RouteId route,
        RouteIndex index,
        VehicleId vehicle,
        AccountId driver,
        Optional<RouteLock> lock,
        CheckState check,
        Optional<Withdrawal> withdrawal,
        Optional<RoutePlan> times,
        Optional<SolveTime> confirmedReturn,
        List<Stop> stops) {

    /** Checks every part is present, possibly empty, and copies the stops. */
    public RouteView {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(planStatus, "planStatus");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(withdrawal, "withdrawal");
        Objects.requireNonNull(times, "times");
        Objects.requireNonNull(confirmedReturn, "confirmedReturn");
        stops = List.copyOf(stops);
    }

    /**
     * One stop of the route (FR-241, FR-385).
     *
     * <p>Built in M0.
     *
     * @param stop the stop
     * @param order its order
     * @param attempt its attempt
     * @param plan its planned times and the leg into it, when the validator timed it (BR-14)
     * @param lateOutcome how a late fact put it here, if one did (DL §8)
     */
    public record Stop(
            StopId stop, OrderId order, Attempt attempt, Optional<StopPlan> plan, Optional<LateOutcome> lateOutcome) {

        /** Checks every part is present, possibly empty. */
        public Stop {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(lateOutcome, "lateOutcome");
        }
    }
}
