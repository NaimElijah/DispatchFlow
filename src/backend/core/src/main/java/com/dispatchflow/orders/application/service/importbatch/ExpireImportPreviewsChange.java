package com.dispatchflow.orders.application.service.importbatch;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.ImportBatchRepository;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link ExpireImportPreviews}: one lists the previews older than their hour,
 * and each preview is expired in one of its own, with its audit record, so one refusal doesn't hold
 * back the rest (ADR-129 decision 3).
 *
 * <p>Built in M2.
 */
@Transactional
public class ExpireImportPreviewsChange {

    private final ImportBatchRepository imports;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the sweep's transactions.
     *
     * @param imports where imports are kept
     * @param clock the time of the sweep
     * @param auditLog the audit trail
     */
    public ExpireImportPreviewsChange(ImportBatchRepository imports, Clock clock, AuditLog auditLog) {
        this.imports = imports;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The previews made more than a preview's hour before now.
     *
     * <p>Built in M2.
     *
     * @return the previews, each once, oldest first
     */
    @Transactional(readOnly = true)
    public List<ImportBatchId> due() {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * Expires one preview through {@code ImportBatch.expire}, deleting its rows, and writes its
     * audit record.
     *
     * <p>Built in M2.
     *
     * @param batch the preview
     * @param call the job's server step
     * @throws RuleViolation with {@code ImportAlreadyConfirmed}, {@code ImportDiscarded} or {@code
     *     ImportExpired} when the preview was confirmed, discarded or expired since it was listed
     * @throws StaleVersion when the preview changed between its read and its save
     */
    public void apply(ImportBatchId batch, CallContext call) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
