package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.api.Refusals;
import com.dispatchflow.archfixture.theta.application.api.Solve;

/**
 * Clean: a use case with no transaction that runs the transactional step it holds through the audit
 * helper, as ADR-150 decision 1 has it.
 */
public final class DelegatesAndRecordsItsRefusalThroughTheHelper {

    private final Solve solve;
    private final AuditLog auditLog;

    /**
     * @param solve the use case that opens the transaction
     * @param auditLog the audit trail, whose refusal the helper records after the rollback
     */
    public DelegatesAndRecordsItsRefusalThroughTheHelper(Solve solve, AuditLog auditLog) {
        this.solve = solve;
        this.auditLog = auditLog;
    }

    /** Calls, through the helper. */
    public void apply() {
        Refusals.recordedBy(auditLog, solve::solve);
    }
}
