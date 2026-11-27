package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;

/** Rule 9: a service with no transaction whose nested class records a success. */
public final class NestsASuccessWithNoTransaction {

    private final Step step;

    /** @param auditLog the audit trail */
    public NestsASuccessWithNoTransaction(AuditLog auditLog) {
        this.step = new Step(auditLog);
    }

    /** Records a success that no change commits with. */
    public void apply() {
        step.record();
    }

    /** The service's own step, with no transaction around it. */
    public static final class Step {

        private final AuditLog auditLog;

        /** @param auditLog the audit trail */
        Step(AuditLog auditLog) {
            this.auditLog = auditLog;
        }

        /** Records a success. */
        void record() {
            auditLog.succeeded();
        }
    }
}
