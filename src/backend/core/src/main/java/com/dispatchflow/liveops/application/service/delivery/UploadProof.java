package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.ProofStore;
import com.dispatchflow.liveops.application.port.out.ProofUpload;
import com.dispatchflow.liveops.domain.delivery.ProofId;
import com.dispatchflow.liveops.domain.delivery.ProofKind;
import com.dispatchflow.shared.application.Clock;

/**
 * A driver's phone uploads a proof of delivery before the report that names it, under the outbox
 * row's ID, so a retried upload is safe (FR-30, ADR-087). The photo is taken inside the app and
 * never reaches the gallery (NFR-204); the store re-encodes it and strips its metadata (NFR-205).
 *
 * <p>Live Operations' domain has no error for a proof over NFR-37's 5 MB or in another format yet,
 * so the refusal this use case raises for one waits for that error.
 *
 * <p>Built in M5. Rows: FR-30, NFR-37, NFR-204, NFR-205.
 */
public final class UploadProof {

    private final ProofStore proofs;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param proofs where proofs are kept
     * @param clock the server's time, for the audit record
     * @param auditLog the audit trail
     */
    public UploadProof(ProofStore proofs, Clock clock, AuditLog auditLog) {
        this.proofs = proofs;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Stores the uploaded proof, and writes the audit record.
     *
     * <p>Built in M5.
     *
     * @param actor the uploading driver
     * @param proof the proof's ID, the outbox row's
     * @param kind a signature or a photo
     * @param upload the image
     */
    public void upload(Actor actor, ProofId proof, ProofKind kind, ProofUpload upload) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
