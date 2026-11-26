package com.dispatchflow.archfixture.theta.adapter.in.listen;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;

/** Rule 9: an adapter that records a success, outside any change's transaction. */
public class AuditsASuccess {

    private final AuditLog auditLog;

    /** @param auditLog the audit trail */
    public AuditsASuccess(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /** Records a success that no change commits with. */
    public void on() {
        auditLog.succeeded();
    }
}
