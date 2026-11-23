package com.dispatchflow.orders.application.service.importbatch;

import com.dispatchflow.audit.application.api.CallContext;

/**
 * The hourly sweep that expires import previews whose hour has passed, deleting their rows (FR-399,
 * NFR-31, ADR-086, DL §10.3): {@code ImportBatch.expire}, one preview per transaction. A preview
 * confirmed or discarded since it was listed refuses, and the sweep moves on to the next.
 *
 * <p>It lists the previews due, then expires them one by one, and opens no transaction of its own:
 * {@link ExpireImportPreviewsChange} holds each preview's (ADR-129).
 *
 * <p>Built in M2.
 */
public final class ExpireImportPreviews {

    private final ExpireImportPreviewsChange change;

    /**
     * Creates the job.
     *
     * @param change the transaction that lists the previews due, and each preview's own
     */
    public ExpireImportPreviews(ExpireImportPreviewsChange change) {
        this.change = change;
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
