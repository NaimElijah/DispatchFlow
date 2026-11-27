package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule 10: a class with no transaction of its own whose method opens one and records its refusal
 * there, so the refusal's rollback takes the record with it.
 */
public class RecordsItsRefusalInATransactionalMethod {

    private final AuditLog auditLog;

    /** @param auditLog the audit trail */
    public RecordsItsRefusalInATransactionalMethod(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /** Changes, and records its refusal inside the method's transaction. */
    @Transactional
    public void apply() {
        auditLog.refused();
    }
}
