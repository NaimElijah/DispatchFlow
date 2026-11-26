package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import org.springframework.transaction.annotation.Transactional;

/** Rule 9: a transactional service that records a success in a read-only read, which never flushes. */
@Transactional
public class RecordsASuccessInAReadOnlyRead {

    private final AuditLog auditLog;

    /** @param auditLog the audit trail */
    public RecordsASuccessInAReadOnlyRead(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /** Reads, and records a success the read-only transaction loses. */
    @Transactional(readOnly = true)
    public void read() {
        auditLog.succeeded();
    }
}
