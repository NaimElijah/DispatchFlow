package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Optional;

/**
 * One vehicle's and driver's ordered stops, inside {@link Plan} (DL §4.4, §17 "Route"). It is
 * immutable: each change makes a new one, so a plan checks every rule before it replaces any.
 */
record Route(
        RouteId id,
        RouteIndex index,
        VehicleId vehicle,
        AccountId driver,
        Optional<RouteLock> lock,
        CheckState check,
        Optional<Withdrawal> withdrawal,
        Optional<SolveTime> confirmedReturn,
        Optional<RoutePlan> plan,
        List<PlannedStop> stops) {

    Route {
        stops = List.copyOf(stops);
    }

    static Route from(PlanSnapshot.RouteSnapshot snapshot) {
        return new Route(
                snapshot.id(),
                snapshot.index(),
                snapshot.vehicle(),
                snapshot.driver(),
                snapshot.lock(),
                snapshot.check(),
                snapshot.withdrawal(),
                snapshot.confirmedReturn(),
                snapshot.plan(),
                snapshot.stops().stream().map(PlannedStop::from).toList());
    }

    boolean withdrawn() {
        return withdrawal.isPresent();
    }

    List<StopId> stopIds() {
        return stops.stream().map(PlannedStop::id).toList();
    }

    List<StopKey> keys() {
        return stops.stream().map(PlannedStop::key).toList();
    }

    Route withStops(List<PlannedStop> newStops) {
        return new Route(id, index, vehicle, driver, lock, check, withdrawal, confirmedReturn, plan, newStops);
    }

    Route withDriver(AccountId newDriver) {
        return new Route(id, index, vehicle, newDriver, lock, check, withdrawal, confirmedReturn, plan, stops);
    }

    Route withLock(Optional<RouteLock> newLock) {
        return new Route(id, index, vehicle, driver, newLock, check, withdrawal, confirmedReturn, plan, stops);
    }

    Route withCheck(CheckState newCheck) {
        return new Route(id, index, vehicle, driver, lock, newCheck, withdrawal, confirmedReturn, plan, stops);
    }

    Route withConfirmedReturn(Optional<SolveTime> newReturn) {
        return new Route(id, index, vehicle, driver, lock, check, withdrawal, newReturn, plan, stops);
    }

    Route withWithdrawal(Optional<Withdrawal> newWithdrawal) {
        return new Route(id, index, vehicle, driver, lock, check, newWithdrawal, confirmedReturn, plan, stops);
    }

    Route withIndex(RouteIndex newIndex) {
        return new Route(id, newIndex, vehicle, driver, lock, check, withdrawal, confirmedReturn, plan, stops);
    }

    Route withPlan(Optional<RoutePlan> newPlan) {
        return new Route(id, index, vehicle, driver, lock, check, withdrawal, confirmedReturn, newPlan, stops);
    }

    PlanSnapshot.RouteSnapshot snapshot() {
        return new PlanSnapshot.RouteSnapshot(
                id,
                index,
                vehicle,
                driver,
                lock,
                check,
                withdrawal,
                confirmedReturn,
                plan,
                stops.stream().map(PlannedStop::snapshot).toList());
    }

    RevisionRoute revision() {
        return new RevisionRoute(
                id,
                vehicle,
                driver,
                stops.stream()
                        .map(stop -> new RevisionStop(
                                stop.id(),
                                stop.order(),
                                stop.attempt(),
                                stop.plan().map(stopPlan -> stopPlan.times().arrival())))
                        .toList());
    }
}
