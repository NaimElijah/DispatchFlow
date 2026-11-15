package com.dispatchflow.orders.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.ImportBatchRepository;
import com.dispatchflow.shared.application.Clock;

/**
 * The hourly sweep that expires import previews whose hour has passed, deleting their rows (FR-399,
 * NFR-31, ADR-086, DL §10.3): {@code ImportBatch.expire}, one preview per transaction. A preview
 * confirmed or discarded since it was listed refuses, and the sweep moves on to the next.
 *
 * <p>Built in M2.
 */
public final class ExpireImportPreviews {

    private final ImportBatchRepository imports;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param imports where imports are kept
     * @param clock the time of the sweep
     * @param auditLog the audit trail
     */
    public ExpireImportPreviews(ImportBatchRepository imports, Clock clock, AuditLog auditLog) {
        this.imports = imports;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Expires every preview whose hour has passed.
     *
     * <p>Built in M2.
     *
     * @param call the job's server step
     * @return how many previews were expired
     */
    public int expireDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
