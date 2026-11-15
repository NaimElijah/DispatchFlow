package com.dispatchflow.audit.application.service;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.port.out.AuditRecords;

/**
 * The nightly retention job's Audit step: it deletes every audit record older than a year, through
 * the database function that computes its own cutoff (NFR-258, ADR-075, ADR-110 point 2). The
 * scheduler calls it at 03:00 {@code Asia/Jerusalem} (TS §6.3).
 *
 * <p>Built in an unscheduled milestone.
 */
public final class PurgeExpiredAuditRecords {

    private final AuditRecords auditRecords;

    /**
     * Creates the job.
     *
     * @param auditRecords where audit records are kept
     */
    public PurgeExpiredAuditRecords(AuditRecords auditRecords) {
        this.auditRecords = auditRecords;
    }

    /**
     * Deletes the expired audit records.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param call the job's server step
     * @return how many records were deleted
     */
    public int purge(CallContext call) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }
}
