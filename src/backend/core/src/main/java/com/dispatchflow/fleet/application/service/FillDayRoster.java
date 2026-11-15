package com.dispatchflow.fleet.application.service;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.roster.RosterFill;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Fills a date's roster from each driver's defaults and weekly shifts, setting only the values
 * nobody set (FR-54, FR-79, FR-189, ADR-103). A date with no roster yet is opened. A driver whose
 * default can't be used is skipped and reported with the reason, and every other driver is still
 * filled; filling again changes nothing.
 *
 * <p>The driver's standing combines Access's answer on the account with the driver record's own
 * state, read now (ADR-062). Like every changing call it writes its audit record, a refused
 * attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class FillDayRoster {

    private final DayRosterRepository rosters;
    private final DriverRepository drivers;
    private final FleetRegisterRepository registers;
    private final Accounts accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param rosters where the day rosters are kept
     * @param drivers where the driver records are kept
     * @param registers where the fleet register is kept
     * @param accounts Access's answer on whether an account is an active driver
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public FillDayRoster(
            DayRosterRepository rosters,
            DriverRepository drivers,
            FleetRegisterRepository registers,
            Accounts accounts,
            Clock clock,
            AuditLog auditLog) {
        this.rosters = rosters;
        this.drivers = drivers;
        this.registers = registers;
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Fills the roster.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param date the date to fill
     * @return the drivers filled, and the drivers skipped with their reasons
     * @throws NotFound when an account of a driver record no longer exists
     * @throws StaleVersion when the roster changed between its read and its save (BR-76)
     */
    public RosterFill fill(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
