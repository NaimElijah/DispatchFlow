package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Rule 9: a service whose annotation suspends the transaction, so its success commits on its own. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RecordsASuccessWithoutATransaction {

    private final AuditLog auditLog;

    /** @param auditLog the audit trail */
    public RecordsASuccessWithoutATransaction(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /** Records a success with no change around it. */
    public void apply() {
        auditLog.succeeded();
    }
}
