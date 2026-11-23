package com.dispatchflow.fleet.application.service.roster;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * A dispatcher sets a driver's shift on a date (FR-54, FR-190). A driver not yet on the roster is
 * added, available, with their vehicle not set, so a later fill may give them their default vehicle
 * (FR-79). Neither end may fall in the hour the clock skips (BR-111). A date with no roster yet is
 * opened empty, never saved, so the domain answers a driver who is not on it.
 *
 * <p>The driver's standing combines Access's answer on the account with the driver record's own
 * state, read now (ADR-062). Like every changing call it writes its audit record, a refused
 * attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class ChangeShift {

    private final DayRosterRepository rosters;
    private final DriverRepository drivers;
    private final Accounts accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param rosters where the day rosters are kept
     * @param drivers where the driver records are kept
     * @param accounts Access's answer on whether an account is an active driver
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ChangeShift(
            DayRosterRepository rosters, DriverRepository drivers, Accounts accounts, Clock clock, AuditLog auditLog) {
        this.rosters = rosters;
        this.drivers = drivers;
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Sets the shift.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param date the roster's date
     * @param driver the driver's account
     * @param hours the shift's wall-clock hours, placed on the date
     * @param expected the version of the date's roster the caller last read, or empty for a date
     *     with none yet, which sends {@code If-None-Match: *} (BR-76)
     * @throws RuleViolation with Fleet's {@code NotAnActiveDriver} when the driver is not active;
     *     or with the kernel's {@code NonexistentLocalTime} when the shift starts or ends in the
     *     hour the clock skips (BR-111)
     * @throws NotFound when no account has the driver's ID
     * @throws StaleVersion when the roster is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void change(Actor actor, DeliveryDate date, AccountId driver, ShiftHours hours, Optional<Version> expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
