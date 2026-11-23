package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import org.springframework.transaction.annotation.Transactional;

/** Rule 5: a read-only service that writes an audit record. */
@Transactional(readOnly = true)
public class ReadOnlyButAuditing {

    private final AuditLog auditLog;

    /**
     * @param auditLog the auditLog
     */
    public ReadOnlyButAuditing(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /** Writes in a read-only transaction. */
    public void record() {
        auditLog.succeeded();
    }
}
