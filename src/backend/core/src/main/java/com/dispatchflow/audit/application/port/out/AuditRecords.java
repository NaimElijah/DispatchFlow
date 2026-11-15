package com.dispatchflow.audit.application.port.out;

import com.dispatchflow.audit.domain.AuditRecord;

/**
 * Where audit records are kept: appended, never changed, and deleted only by the database function
 * a year after they were made (NFR-23, NFR-258, ADR-075, ADR-110). The core's role may only insert
 * and select records, and execute that function (TS §6.3).
 *
 * <p>Built in M0.
 */
public interface AuditRecords {

    /**
     * Appends a record, in the caller's transaction.
     *
     * <p>Built in M0.
     *
     * @param record the record
     */
    void append(AuditRecord record);

    /**
     * Calls the database function that deletes every record older than a year. The function
     * computes its own cutoff, which matches {@link AuditRecord#RETENTION} (ADR-075, ADR-110 point
     * 2).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @return how many records were deleted
     */
    int purgeExpired();
}
