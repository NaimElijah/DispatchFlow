package com.dispatchflow.fleet.application.service.driver;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's driver step: {@code Driver.anonymize} removes the name and phone of
 * every driver retired a year ago or more, in Israel's calendar, and keeps the record (BR-98,
 * NFR-184, ADR-075). Each record is anonymized in its own transaction, so one stale save doesn't
 * hold back the rest; the next night retries it. A driver whose retention isn't over refuses with
 * {@code RetentionNotOver}, and the job moves on.
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeRetiredDrivers {

    private final DriverRepository drivers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param drivers where the driver records are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public AnonymizeRetiredDrivers(DriverRepository drivers, Clock clock, AuditLog auditLog) {
        this.drivers = drivers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Anonymizes the drivers whose retention is over.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many drivers were anonymized
     */
    public int anonymizeDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
