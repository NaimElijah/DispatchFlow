package com.dispatchflow.audit.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.port.out.AuditRecords;
import com.dispatchflow.shared.application.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * The nightly retention job's Audit step: it deletes every audit record older than a year, through
 * the database function that computes its own cutoff (NFR-258, ADR-075, ADR-110 point 2). The
 * scheduler calls it at 03:00 {@code Asia/Jerusalem} (TS §6.3).
 *
 * <p>It records its own run through {@link AuditLog#succeeded}, in the purge's transaction and
 * naming no record, as every other step of the nightly run records its own: so an Admin can see
 * that the purge ran, and when (NFR-93, FR-126). The new record is never older than a year, so the
 * purge never deletes it.
 *
 * <p>To Be Implemented. It comes with the whole nightly retention run (ADR-121).
 */
@Transactional
public class PurgeExpiredAuditRecords {

    private final AuditRecords auditRecords;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param auditRecords where audit records are kept
     * @param clock the time the run is recorded at
     * @param auditLog where the run's own audit record is written
     */
    public PurgeExpiredAuditRecords(AuditRecords auditRecords, Clock clock, AuditLog auditLog) {
        this.auditRecords = auditRecords;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Deletes the expired audit records, and records the run.
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many records were deleted
     */
    public int purge(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
