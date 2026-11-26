package com.dispatchflow.archfixture.theta.adapter.in.listen;

import com.dispatchflow.archfixture.theta.application.service.AuditLogService;

/** Rule 9: an adapter that records a success through the audit port's implementation. */
public class AuditsThroughTheService {

    private final AuditLogService auditLog;

    /** @param auditLog the audit port's implementation */
    public AuditsThroughTheService(AuditLogService auditLog) {
        this.auditLog = auditLog;
    }

    /** Records a success that no change commits with. */
    public void on() {
        auditLog.succeeded();
    }
}
