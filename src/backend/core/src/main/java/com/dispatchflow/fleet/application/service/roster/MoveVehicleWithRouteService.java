package com.dispatchflow.fleet.application.service.roster;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.application.api.MoveVehicleWithRoute;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Fleet's implementation of {@code MoveVehicleWithRoute}: after Planning hands a route over, {@code
 * DayRoster.moveVehicleWithRoute} gives the date's vehicle to the new driver, so the roster follows
 * the plan (FR-191). It writes its audit record with the listener's server step (NFR-23, ADR-110).
 *
 * <p>Built in M9.
 */
public final class MoveVehicleWithRouteService implements MoveVehicleWithRoute {

    private final DayRosterRepository rosters;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the service.
     *
     * @param rosters where the day rosters are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public MoveVehicleWithRouteService(DayRosterRepository rosters, Clock clock, AuditLog auditLog) {
        this.rosters = rosters;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     *
     * @throws StaleVersion when the date's roster changed between its read and its save; Planning's
     *     listener adapter on {@code RouteHandedOver} retries at once, up to three times (DL §11)
     */
    @Override
    public void move(DeliveryDate date, VehicleId vehicle, AccountId from, AccountId to, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
