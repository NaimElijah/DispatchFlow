package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.proof.ProofStore;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's Live Operations step (NFR-31, NFR-184, NFR-260, ADR-075, ADR-107): it
 * deletes positions 7 days after receipt and proof files 90 days after they were stored, and erases
 * the personal data each due delivery holds through {@code Delivery.eraseExpired}, one delivery per
 * transaction, keeping every record and kind. The cutoffs come from {@code RetentionPeriods}. The
 * scheduler calls it at 03:00 {@code Asia/Jerusalem} (DL §10.3).
 *
 * <p>{@link EraseExpiredExecutionDataChange} holds each transaction: one deletes the positions, and
 * one per due delivery erases it, as the job loops over the due stops. The proof files are deleted
 * through {@link ProofStore#deleteOlderThan}, on disk, outside any transaction.
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121). Rows: NFR-184, NFR-260, ADR-075.
 */
public final class EraseExpiredExecutionData {

    private final ProofStore proofs;
    private final Clock clock;
    private final EraseExpiredExecutionDataChange change;

    /**
     * Creates the job.
     *
     * @param proofs where proof files are kept
     * @param clock the server's time, for the proof files' cutoff
     * @param change the job's transactions
     */
    public EraseExpiredExecutionData(ProofStore proofs, Clock clock, EraseExpiredExecutionDataChange change) {
        this.proofs = proofs;
        this.clock = clock;
        this.change = change;
    }

    /**
     * Erases what is due. A delivery another writer saved meanwhile is left for the next night.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many deliveries, positions and files it erased or deleted, together
     */
    public int erase(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
