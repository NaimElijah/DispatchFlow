package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule 10's exception: a {@code Change} that answers a refusal without letting it leave, so its
 * transaction commits the record alone. It is clean only while listed.
 */
@Transactional
public class AnswersAlikeChange {

    private final Ledgers ledgers;
    private final AuditLog auditLog;

    /**
     * @param ledgers the ledgers
     * @param auditLog the audit trail
     */
    public AnswersAlikeChange(Ledgers ledgers, AuditLog auditLog) {
        this.ledgers = ledgers;
        this.auditLog = auditLog;
    }

    /** Records a refusal raised before anything changed, and answers as if it succeeded. */
    public void apply() {
        if (ledgers.count() == 0) {
            auditLog.refused();
        }
    }
}
