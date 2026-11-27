package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import org.springframework.transaction.annotation.Transactional;

/** Clean for rule 9: a transactional service whose nested class records the success it commits. */
@Transactional
public class RecordsInANestedClass {

    private final Step step;

    /** @param auditLog the audit trail */
    public RecordsInANestedClass(AuditLog auditLog) {
        this.step = new Step(auditLog);
    }

    /** Changes, and records the change through its step. */
    public void apply() {
        step.record();
    }

    /** The service's own step, run in its transaction. */
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
