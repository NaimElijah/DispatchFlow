package com.dispatchflow.fleet.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;

/**
 * Moves a vehicle on a date's roster from one driver to another after Planning handed the route
 * over, so the roster follows the plan (FR-191, DL §2.2). Planning's listener on {@code
 * RouteHandedOver} calls it.
 *
 * <p>Built in M9.
 */
public interface MoveVehicleWithRoute {

    /**
     * Gives the vehicle to the new driver and leaves the old one with no vehicle. Delivery of the
     * event is at least once, so a repeat changes nothing.
     *
     * <p>Built in M9.
     *
     * @param date the route's date
     * @param vehicle the route's vehicle
     * @param from the driver the route was handed away from
     * @param to the driver the route was handed to
     * @param call the listener's server step
     * @throws com.dispatchflow.shared.RuleViolation with Fleet's errors from {@code
     *     DayRoster.moveVehicleWithRoute} when the roster refuses the move
     */
    void move(DeliveryDate date, VehicleId vehicle, AccountId from, AccountId to, CallContext call);
}
