package com.dispatchflow.orders.application.service.importbatch;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.ImportBatchRepository;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher discards an import preview: {@code ImportBatch.discard}, and its rows are deleted at
 * once (FR-399, NFR-31, ADR-086).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class DiscardOrderImport {

    private final ImportBatchRepository imports;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param imports where imports are kept
     * @param clock the time of discarding
     * @param auditLog the audit trail
     */
    public DiscardOrderImport(ImportBatchRepository imports, Clock clock, AuditLog auditLog) {
        this.imports = imports;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Discards the preview.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param batch the import
     * @param expected the version of the preview the dispatcher discarded (BR-76)
     * @throws NotFound when no import has the ID
     * @throws RuleViolation with {@code ImportAlreadyConfirmed}, {@code ImportDiscarded} or {@code
     *     ImportExpired}
     * @throws StaleVersion when the import changed since {@code expected}
     */
    public void discard(Actor actor, ImportBatchId batch, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
