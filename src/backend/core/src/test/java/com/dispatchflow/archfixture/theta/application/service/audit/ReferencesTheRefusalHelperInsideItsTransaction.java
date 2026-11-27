package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.api.Refusals;
import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import java.util.function.BiConsumer;
import org.springframework.transaction.annotation.Transactional;

/** Rule 10: a transactional service that hands the audit helper on as a method reference. */
@Transactional
public class ReferencesTheRefusalHelperInsideItsTransaction {

    private final Ledgers ledgers;
    private final AuditLog auditLog;

    /**
     * @param ledgers the ledgers
     * @param auditLog the audit trail
     */
    public ReferencesTheRefusalHelperInsideItsTransaction(Ledgers ledgers, AuditLog auditLog) {
        this.ledgers = ledgers;
        this.auditLog = auditLog;
    }

    /** Changes through the helper, reached by reference, inside its transaction. */
    public void apply() {
        BiConsumer<AuditLog, Runnable> recorded = Refusals::recordedBy;
        recorded.accept(auditLog, () -> ledgers.count());
    }
}
