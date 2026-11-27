package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.api.Solve;

/**
 * Clean: a use case with no transaction that records its refusal once the use case it called has
 * rolled back, as ADR-149 decision 1 has it.
 */
public final class DelegatesAndRecordsItsRefusal {

    private final Solve solve;
    private final AuditLog auditLog;

    /**
     * @param solve the use case that opens the transaction
     * @param auditLog the audit trail, whose refusal it records after the rollback
     */
    public DelegatesAndRecordsItsRefusal(Solve solve, AuditLog auditLog) {
        this.solve = solve;
        this.auditLog = auditLog;
    }

    /** Calls, and records a refusal once its transaction has rolled back. */
    public void apply() {
        try {
            solve.solve();
        } catch (IllegalStateException refusal) {
            auditLog.refused();
            throw refusal;
        }
    }
}
