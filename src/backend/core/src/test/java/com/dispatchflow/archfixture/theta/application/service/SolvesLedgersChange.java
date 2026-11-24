package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Clean: the transaction of a split use case. */
@Transactional
public class SolvesLedgersChange {

    private final Ledgers ledgers;
    private final AuditLog auditLog;

    /**
     * @param ledgers the ledgers
     * @param auditLog the auditLog
     */
    public SolvesLedgersChange(Ledgers ledgers, AuditLog auditLog) {
        this.ledgers = ledgers;
        this.auditLog = auditLog;
    }

    /** Changes the ledgers and audits it. */
    public void apply() {
        ledgers.count();
        auditLog.succeeded();
    }
}
