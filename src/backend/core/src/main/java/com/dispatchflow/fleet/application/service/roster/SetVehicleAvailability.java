package com.dispatchflow.fleet.application.service.roster;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.roster.Availability;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * A dispatcher marks a vehicle unavailable on a date, during the day (FR-07) or before it starts
 * (FR-301), whether or not anyone drives it; or available again, effective from the next solve or
 * re-optimization (FR-300). It keeps its driver, and BR-20 refuses publishing it. A date with no
 * roster yet is opened empty, never saved, so a vehicle can be marked before anyone is
 * rostered; the vehicle's standing comes from the register. Like every changing call it writes its
 * audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class SetVehicleAvailability {

    private final DayRosterRepository rosters;
    private final FleetRegisterRepository registers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param rosters where the day rosters are kept
     * @param registers where the fleet register is kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SetVehicleAvailability(
            DayRosterRepository rosters, FleetRegisterRepository registers, Clock clock, AuditLog auditLog) {
        this.rosters = rosters;
        this.registers = registers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Sets the vehicle's availability.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param date the roster's date
     * @param vehicle the vehicle
     * @param availability whether the vehicle may be used that date
     * @param expected the version of the date's roster the caller last read, or empty for a date
     *     with none yet, which sends {@code If-None-Match: *} (BR-76)
     * @throws RuleViolation with Fleet's {@code VehicleNotActive} when a vehicle marked unavailable
     *     is retired or not in the register
     * @throws StaleVersion when the roster is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void set(
            Actor actor, DeliveryDate date, VehicleId vehicle, Availability availability, Optional<Version> expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
