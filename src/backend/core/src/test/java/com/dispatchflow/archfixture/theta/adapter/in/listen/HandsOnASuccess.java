package com.dispatchflow.archfixture.theta.adapter.in.listen;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;

/** Rule 9: an adapter that hands a reference to the success on, outside any change's transaction. */
public class HandsOnASuccess {

    private final AuditLog auditLog;

    /** @param auditLog the audit trail */
    public HandsOnASuccess(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /**
     * Hands the success on as a callback.
     *
     * @return the callback, which records a success no change commits with
     */
    public Runnable later() {
        return auditLog::succeeded;
    }
}
