package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.PositionLog;
import com.dispatchflow.liveops.application.port.out.ProofStore;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's Live Operations step (NFR-31, NFR-184, NFR-258, ADR-075, ADR-107): it
 * deletes positions 7 days after receipt and proof files 90 days after they were stored, and erases
 * the personal data each due delivery holds through {@code Delivery.eraseExpired}, one delivery per
 * transaction, keeping every record and kind. The cutoffs come from {@code RetentionPeriods}. The
 * scheduler calls it at 03:00 {@code Asia/Jerusalem} (DL §10.3).
 *
 * <p>Built in M8. Rows: NFR-258, ADR-075.
 */
public final class EraseExpiredExecutionData {

    private final DeliveryRepository deliveries;
    private final PositionLog positions;
    private final ProofStore proofs;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param deliveries where deliveries are kept
     * @param positions where positions are kept
     * @param proofs where proof files are kept
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public EraseExpiredExecutionData(
            DeliveryRepository deliveries, PositionLog positions, ProofStore proofs, Clock clock, AuditLog auditLog) {
        this.deliveries = deliveries;
        this.positions = positions;
        this.proofs = proofs;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Erases what is due. A delivery another writer saved meanwhile is left for the next night.
     *
     * <p>Built in M8.
     *
     * @param call the job's server step
     * @return how many deliveries, positions and files it erased or deleted, together
     */
    public int erase(CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
