package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.api.Refusals;
import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule 10: a {@code Change} on the list that runs its work through the audit helper. The helper
 * rethrows the refusal, which rolls its record back, so the list doesn't excuse it.
 */
@Transactional
public class AnswersAlikeThroughTheHelperChange {

    private final Ledgers ledgers;
    private final AuditLog auditLog;

    /**
     * @param ledgers the ledgers
     * @param auditLog the audit trail
     */
    public AnswersAlikeThroughTheHelperChange(Ledgers ledgers, AuditLog auditLog) {
        this.ledgers = ledgers;
        this.auditLog = auditLog;
    }

    /** Changes through the helper. */
    public void apply() {
        Refusals.recordedBy(auditLog, () -> ledgers.count());
    }
}
