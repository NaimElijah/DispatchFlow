package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.api.Refusals;
import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule 10: a one-class service that runs its own work through the audit helper, inside the
 * transaction the refusal rolls back.
 */
@Transactional
public class RecordsItsRefusalThroughTheHelperInsideItsTransaction {

    private final Ledgers ledgers;
    private final AuditLog auditLog;

    /**
     * @param ledgers the ledgers
     * @param auditLog the audit trail
     */
    public RecordsItsRefusalThroughTheHelperInsideItsTransaction(Ledgers ledgers, AuditLog auditLog) {
        this.ledgers = ledgers;
        this.auditLog = auditLog;
    }

    /** Changes, and loses its refusal's record with the rollback. */
    public void apply() {
        Refusals.recordedBy(auditLog, () -> ledgers.count());
    }
}
