package com.dispatchflow.liveops.application.service.delivery.proof;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.domain.proof.ProofId;
import com.dispatchflow.liveops.domain.proof.ProofKind;
import com.dispatchflow.shared.application.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link UploadProof} (FR-30, NFR-23): it writes the audit record of an upload
 * the proof store kept, naming the proof's ID and kind and no personal data (NFR-94).
 *
 * <p>Built in M5.
 */
@Transactional
public class UploadProofChange {

    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param clock the server's time, for the audit record
     * @param auditLog the audit trail
     */
    public UploadProofChange(Clock clock, AuditLog auditLog) {
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Writes the audit record of the stored upload.
     *
     * <p>Built in M5.
     *
     * @param actor the uploading driver
     * @param proof the proof's ID, the outbox row's
     * @param kind a signature or a photo
     */
    public void apply(Actor actor, ProofId proof, ProofKind kind) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
