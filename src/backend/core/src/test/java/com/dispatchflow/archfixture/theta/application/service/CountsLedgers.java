package com.dispatchflow.archfixture.theta.application.service;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Clean: a changing service, transactional at class level. */
@Transactional
public class CountsLedgers {

    private final Ledgers ledgers;
    private final AuditLog auditLog;

    /**
     * @param ledgers the ledgers
     * @param auditLog the auditLog
     */
    public CountsLedgers(Ledgers ledgers, AuditLog auditLog) {
        this.ledgers = ledgers;
        this.auditLog = auditLog;
    }

    /** Counts, and audits the count. */
    public void count() {
        ledgers.count();
        auditLog.succeeded();
    }
}
