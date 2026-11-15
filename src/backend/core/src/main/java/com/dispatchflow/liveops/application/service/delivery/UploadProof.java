package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.proof.ProofFormatRefused;
import com.dispatchflow.liveops.application.port.out.proof.ProofIdTaken;
import com.dispatchflow.liveops.application.port.out.proof.ProofImageTooLarge;
import com.dispatchflow.liveops.application.port.out.proof.ProofImageUnreadable;
import com.dispatchflow.liveops.application.port.out.proof.ProofStore;
import com.dispatchflow.liveops.application.port.out.proof.ProofUpload;
import com.dispatchflow.liveops.application.port.out.proof.ProofUploadsCapped;
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
 * an oversized upload is cut off before it is held in memory. The store then runs the cheap checks
 * before it reads the image: it refuses an ID another upload holds ({@link ProofIdTaken}), answers
 * a same-ID retry with nothing changed, and refuses one upload more than the uploader's cap ({@link
 * ProofUploadsCapped}). Only then does it refuse a file whose leading bytes aren't the declared
 * JPEG or PNG ({@link ProofFormatRefused}, 422), read the pixel dimensions with the declared
 * format's reader alone and refuse an image above its cap before decoding it ({@link
 * ProofImageTooLarge}, 422), and refuse a file that reader can't decode ({@link
 * ProofImageUnreadable}, 422), so each refusal names its reason (NFR-37, NFR-205).
 *
 * <p>An upload stays unnamed until the report that names it arrives. The store bounds such uploads:
 * it deletes one that no report names within a window longer than the 14 days a phone may hold its
 * queued reports offline (ADR-087, NFR-235), so a phone's late report still finds its upload, and
 * it caps how many unnamed uploads one driver may hold, refusing one more with {@link
 * ProofUploadsCapped}, which the web adapter answers with 429 and {@code Retry-After} so the phone
 * waits instead of marking its row refused. Both are the store adapter's named settings, whose
 * values M5 fixes.
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M5. Rows: FR-30, FR-367, NFR-37, NFR-204, NFR-205, NFR-235.
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
     * @throws ProofFormatRefused when the file's leading bytes are not the declared JPEG or PNG
     * @throws ProofImageTooLarge when the image's pixel dimensions are above the store's cap
     * @throws ProofImageUnreadable when the declared format's reader can't decode the file
     * @throws ProofUploadsCapped when the driver already holds as many uploads that no report names
     *     as the store's cap allows
     */
    public void upload(Actor actor, ProofId proof, ProofKind kind, ProofUpload upload) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
