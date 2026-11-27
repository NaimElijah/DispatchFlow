package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule 9: a transactional service that records a success inside a lambda of a read-only read,
 * which never flushes.
 */
@Transactional
public class RecordsASuccessInALambdaOfAReadOnlyRead {

    private final AuditLog auditLog;

    /** @param auditLog the audit trail */
    public RecordsASuccessInALambdaOfAReadOnlyRead(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /** Reads, and records, through a lambda, a success the read-only transaction loses. */
    @Transactional(readOnly = true)
    public void read() {
        Runnable record = () -> auditLog.succeeded();
        record.run();
    }
}
