package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.api.Solve;

/** Rule 9: a service with no transaction that records a success, apart from any change. */
public final class DelegatesAndAudits {

    private final Solve solve;
    private final AuditLog auditLog;

    /**
     * @param solve the other module's use case
     * @param auditLog the audit trail, whose success it records with no transaction
     */
    public DelegatesAndAudits(Solve solve, AuditLog auditLog) {
        this.solve = solve;
        this.auditLog = auditLog;
    }

    /** Calls, then audits. */
    public void apply() {
        solve.solve();
        auditLog.succeeded();
    }
}
