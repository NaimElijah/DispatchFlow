package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.PlanningError.DriverRoutedTwice;
import com.dispatchflow.planning.domain.PlanningError.OrderAlreadyInPlan;
import com.dispatchflow.planning.domain.PlanningError.VehicleRoutedTwice;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a solution must hold to be a plan's routes and drops, checked before a job succeeds and when
 * a proposal is created or loaded, so no validated solution can trap the plan that applies it (ADR-101,
 * ADR-112).
 */
final class SolutionShape {

    private SolutionShape() {}

    /**
     * The first rule a solution's routes and drops break: an order and attempt once (BR-07), a
     * vehicle on one route, and a driver on one route it isn't withdrawn from (BR-39).
     *
     * @return the error, or empty when it holds every rule
     */
    static Optional<PlanningError> brokenBy(
            List<SolvedRoute> routes, List<DroppedOrder> dropped, List<VehicleWithdrawal> withdrawn) {
        Set<StopKey> seen = new HashSet<>();
        List<StopKey> keys = new ArrayList<>();
        routes.forEach(route -> route.stops().forEach(stop -> keys.add(new StopKey(stop.order(), stop.attempt()))));
        dropped.forEach(drop -> keys.add(new StopKey(drop.order(), drop.attempt())));
        for (StopKey key : keys) {
            if (!seen.add(key)) {
                return Optional.of(new OrderAlreadyInPlan(key.order(), key.attempt()));
            }
        }
        Set<VehicleId> withdrawnVehicles = new HashSet<>();
        withdrawn.forEach(withdrawal -> withdrawnVehicles.add(withdrawal.vehicle()));
        Set<VehicleId> vehicles = new HashSet<>();
        Set<AccountId> drivers = new HashSet<>();
        for (SolvedRoute route : routes) {
            if (!vehicles.add(route.vehicle())) {
                return Optional.of(new VehicleRoutedTwice(route.vehicle()));
            }
            if (!withdrawnVehicles.contains(route.vehicle()) && !drivers.add(route.driver())) {
                return Optional.of(new DriverRoutedTwice(route.driver()));
            }
        }
        return Optional.empty();
    }
}
