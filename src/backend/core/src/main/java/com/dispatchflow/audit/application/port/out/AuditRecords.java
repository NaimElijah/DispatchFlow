package com.dispatchflow.audit.application.port.out;

import com.dispatchflow.audit.domain.AuditRecord;

/**
 * Where audit records are kept: appended, never changed, and deleted only by the database function
 * a year after they were made (NFR-23, NFR-258, ADR-075, ADR-110). The core's role may only insert
 * and select records, and execute that function (TS §6.3).
 *
 * <p>{@link #purgeExpired} is built in M13.
 */
public interface AuditRecords {

    /**
     * Appends a record, in the caller's transaction.
     *
     * @param record the record
     */
    void append(AuditRecord record);

    /**
     * Calls the database function that deletes every record older than a year. The function
     * computes its own cutoff, which matches {@link AuditRecord#RETENTION} (ADR-075, ADR-110 point
     * 2).
     *
     * <p>Built in M13.
     *
     * @return how many records were deleted
     */
    int purgeExpired();
}
