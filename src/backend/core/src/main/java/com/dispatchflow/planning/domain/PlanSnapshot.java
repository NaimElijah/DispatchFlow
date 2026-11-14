package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link Plan}'s whole present state, which the repository stores and {@link Plan#restore}
 * rebuilds (DL §4.4 "What {@code Plan} loads"). Past revisions are not part of it (ADR-059).
 *
 * @param id the plan
 * @param version the version it was loaded at
 * @param date its delivery date
 * @param openedAt when it was opened
 * @param status its state
 * @param applied the solve its routes came from
 * @param routes its routes, in the plan's order
 * @param dropped its dropped orders, ordered by order ID and attempt
 * @param pins its pins, ordered by order ID
 * @param violations its open violations
 * @param undo the edits since the last solve, oldest first
 * @param redo the undone edits, oldest first
 */
public record PlanSnapshot(
        PlanId id,
        Version version,
        DeliveryDate date,
        Instant openedAt,
        Plan.Status status,
        Optional<AppliedSolve> applied,
        List<RouteSnapshot> routes,
        List<DroppedOrder> dropped,
        List<Pin> pins,
        List<Violation> violations,
        List<Edit> undo,
        List<Edit> redo) {

    /** Checks every part is present, and copies the lists. */
    public PlanSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(applied, "applied");
        routes = List.copyOf(routes);
        dropped = List.copyOf(dropped);
        pins = List.copyOf(pins);
        violations = List.copyOf(violations);
        undo = List.copyOf(undo);
        redo = List.copyOf(redo);
    }

    /**
     * One route of the plan.
     *
     * @param id the route
     * @param index its label and colour slot (FR-148)
     * @param vehicle its vehicle
     * @param driver its driver
     * @param lock its lock, if locked (FR-147)
     * @param check whether the validator judged it since it changed (FR-395)
     * @param withdrawal its withdrawal, if withdrawn mid-route (FR-267)
     * @param confirmedReturn the return time a confirmed hand-over set, if one did (FR-394)
     * @param plan its departure, return and last leg
     * @param stops its stops, in order
     */
    public record RouteSnapshot(
            RouteId id,
            RouteIndex index,
            VehicleId vehicle,
            AccountId driver,
            Optional<RouteLock> lock,
            CheckState check,
            Optional<Withdrawal> withdrawal,
            Optional<DaySeconds> confirmedReturn,
            RoutePlan plan,
            List<StopSnapshot> stops) {

        /** Checks every part is present, and copies the stops. */
        public RouteSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(lock, "lock");
            Objects.requireNonNull(check, "check");
            Objects.requireNonNull(withdrawal, "withdrawal");
            Objects.requireNonNull(confirmedReturn, "confirmedReturn");
            Objects.requireNonNull(plan, "plan");
            stops = List.copyOf(stops);
        }
    }

    /**
     * One stop of a route.
     *
     * @param id the stop
     * @param order its order
     * @param attempt its attempt
     * @param plan its planned times, if it has them
     * @param late how it joined the route after a late fact, if it did (DL §8)
     */
    public record StopSnapshot(
            StopId id, OrderId order, Attempt attempt, Optional<StopPlan> plan, Optional<LateOutcome> late) {

        /** Checks every part is present, possibly empty. */
        public StopSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(late, "late");
        }
    }
}
