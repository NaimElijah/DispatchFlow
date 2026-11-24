package com.dispatchflow.fleet.application.service.driver;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link AnonymizeRetiredDrivers}: one lists the drivers due, and each driver
 * is anonymized in one of its own, with its audit record, so one stale save doesn't hold back the
 * rest (ADR-129 decision 3).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
@Transactional
public class AnonymizeRetiredDriversChange {

    private final DriverRepository drivers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job's transactions.
     *
     * @param drivers where the driver records are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public AnonymizeRetiredDriversChange(DriverRepository drivers, Clock clock, AuditLog auditLog) {
        this.drivers = drivers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The drivers whose retention may be over now: retired at least a year ago, and not yet
     * anonymized. {@link #apply} decides each one.
     *
     * <p>Built in M13.
     *
     * @return the drivers' accounts, in any order
     */
    @Transactional(readOnly = true)
    public List<AccountId> due() {
        throw new UnsupportedOperationException("Built in M13");
    }

    /**
     * Anonymizes one driver through {@code Driver.anonymize}, and writes its audit record.
     *
     * <p>Built in M13.
     *
     * @param driver the driver's account
     * @param call the job's server step
     * @throws RuleViolation with Fleet's {@code RetentionNotOver} when the driver is active, or
     *     was retired less than a year ago
     * @throws StaleVersion when the record changed between its read and its save; the next night
     *     retries it
     */
    public void apply(AccountId driver, CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
