package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.ProofIdTaken;
import com.dispatchflow.liveops.application.port.out.ProofImageTooLarge;
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
 * <p>It passes the acting driver's account and the stated kind to {@link ProofStore#store}, which
 * binds the file to both, so a report names only its own driver's proof of the kind it states
 * (FR-367, BR-02). The web adapter caps the request body at NFR-37's 5 MB while it streams it, so
 * an oversized upload is cut off before it is held in memory; the store then refuses an image whose
 * header gives pixel dimensions above its cap, before decoding it.
 *
 * <p>An upload stays unnamed until the report that names it arrives. The store bounds such uploads:
 * it deletes one that no report names within a window, and caps how many unnamed uploads one driver
 * may hold. Both are the store adapter's named settings, whose values M5 fixes.
 *
 * <p>Live Operations' domain has no error for a proof in another format yet, so the refusal this
 * use case raises for one waits for that error.
 *
 * <p>Built in M5. Rows: FR-30, FR-367, NFR-37, NFR-204, NFR-205.
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
     * @param upload the image, within the 5 MB the web adapter let through
     * @throws ProofIdTaken when another driver holds the ID, this driver holds it under the other
     *     kind, or the ID is retired because its file was deleted
     * @throws ProofImageTooLarge when the image's pixel dimensions are above the store's cap
     */
    public void upload(Actor actor, ProofId proof, ProofKind kind, ProofUpload upload) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
