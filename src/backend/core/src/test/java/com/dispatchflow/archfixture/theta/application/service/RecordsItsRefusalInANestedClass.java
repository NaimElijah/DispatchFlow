package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule 10: a transactional service whose nested class records the refusal, inside the transaction
 * the refusal rolls back.
 */
@Transactional
public class RecordsItsRefusalInANestedClass {

    private final Step step;

    /** @param auditLog the audit trail */
    public RecordsItsRefusalInANestedClass(AuditLog auditLog) {
        this.step = new Step(auditLog);
    }

    /** Changes, and records its refusal through its step. */
    public void apply() {
        step.refuse();
    }

    /** The service's own step, run in its transaction. */
    public static final class Step {

        private final AuditLog auditLog;

        /** @param auditLog the audit trail */
        Step(AuditLog auditLog) {
            this.auditLog = auditLog;
        }

        /** Records a refusal. */
        void refuse() {
            auditLog.refused();
        }
    }
}
