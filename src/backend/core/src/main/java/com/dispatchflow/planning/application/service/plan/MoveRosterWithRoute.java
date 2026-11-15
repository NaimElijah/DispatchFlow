package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.application.api.MoveVehicleWithRoute;
import com.dispatchflow.planning.events.RouteHandedOver;

/**
 * Moves the route's vehicle on the date's roster to the driver it was handed to, so the roster follows
 * the plan (FR-191, DL §2.2, §6.2). Its listener calls it on {@link RouteHandedOver}. It changes no
 * Planning aggregate: Fleet's {@code MoveVehicleWithRoute} changes the roster, ignores a repeat, and
 * writes its own audit record with the listener's server step, so this needs no {@code AuditLog}.
 *
 * <p>Built in M9.
 */
public final class MoveRosterWithRoute {

    private final MoveVehicleWithRoute moveVehicleWithRoute;

    /**
     * Creates the use case.
     *
     * @param moveVehicleWithRoute Fleet's API that moves the vehicle on the roster
     */
    public MoveRosterWithRoute(MoveVehicleWithRoute moveVehicleWithRoute) {
        this.moveVehicleWithRoute = moveVehicleWithRoute;
    }

    /**
     * Moves the vehicle to the new driver.
     *
     * <p>Built in M9.
     *
     * @param event the hand-over
     * @param call the listener's server step, which Fleet's call takes and audits (ADR-110)
     * @throws com.dispatchflow.shared.RuleViolation with Fleet's errors from {@code
     *     DayRoster.moveVehicleWithRoute} when the roster refuses the move
     */
    public void on(RouteHandedOver event, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
