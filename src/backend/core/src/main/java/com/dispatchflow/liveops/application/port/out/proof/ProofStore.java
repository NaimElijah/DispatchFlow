package com.dispatchflow.liveops.application.port.out.proof;

import com.dispatchflow.liveops.domain.delivery.ProofId;
import com.dispatchflow.liveops.domain.delivery.ProofKind;
import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Where proof-of-delivery files are kept: the VM's disk, served only through authorized calls
 * (ADR-027, NFR-38, TS §6.4). A file is deleted 90 days after it was stored, and the delivery keeps
 * the proof's kind (NFR-184, ADR-107). Each file is bound to the account that uploaded it and to its
 * kind, so a report can name only its own driver's proof (FR-367, BR-02).
 *
 * <p><b>Retired IDs.</b> When a file is deleted, its ID's binding to the uploader and kind stays, as
 * a tombstone. So a retired ID is never stored again, and an old report can never come to name
 * another upload's file.
 *
 * <p><b>Uploads no report names.</b> The phone uploads a proof before the report that names it
 * (ADR-087), so an upload can stay unnamed. The adapter bounds them: it deletes an upload that no
 * report names within a window, keeping its binding as above, and caps how many unnamed uploads one
 * uploader may hold, refusing one more with {@link ProofUploadsCapped}. The window is longer than
 * the 14 days a phone may hold its queued reports offline (ADR-087, NFR-235), so a phone's late
 * report still finds its upload. The window and the cap are the adapter's named settings, whose
 * values M5 fixes.
 *
 * <p>Built in M5 and M8, method by method.
 */
public interface ProofStore {

    /**
     * Stores an uploaded proof under the phone's outbox row ID, bound to its uploader and kind.
     * Storing an ID the same uploader already stored under the same kind changes nothing, so the
     * phone's retried upload is safe (ADR-087).
     *
     * <p>The adapter checks the image in this order, before it writes anything (NFR-37, NFR-205):
     *
     * <ol>
     *   <li>the leading bytes must be the signature of the declared {@link ProofMediaType}, else
     *       {@link ProofFormatRefused};
     *   <li>it takes the declared format's reader alone, never one the content picks, and reads the
     *       pixel dimensions from that same reader's header before decoding, refusing one above its
     *       named cap with {@link ProofImageTooLarge}, so a small file that unpacks into a huge
     *       bitmap never reaches the decoder;
     *   <li>it decodes with that reader, and a file it can't decode is {@link ProofImageUnreadable};
     *   <li>it re-encodes the image and strips its metadata, and writes the result.
     * </ol>
     *
     * <p>The cap is the adapter's setting, whose value M5 fixes.
     *
     * <p>Built in M5.
     *
     * @param uploader the account of the uploading driver
     * @param proof the proof's ID
     * @param kind a signature or a photo
     * @param upload the uploaded image
     * @throws ProofIdTaken when another uploader holds the ID, the same uploader holds it under the
     *     other kind, or the ID is retired because its file was deleted
     * @throws ProofFormatRefused when the file's leading bytes are not the declared JPEG or PNG
     * @throws ProofImageTooLarge when the declared format's reader gives pixel dimensions above the
     *     cap
     * @throws ProofImageUnreadable when the declared format's reader can't decode the file
     * @throws ProofUploadsCapped when the uploader already holds as many uploads that no report
     *     names as the cap allows, and this one isn't a retry of one of them
     */
    void store(AccountId uploader, ProofId proof, ProofKind kind, ProofUpload upload);

    /**
     * Who uploaded a proof, and as which kind, without reading the file.
     *
     * <p>Built in M5.
     *
     * @param proof the proof's ID
     * @return the uploader and the kind, or empty when none was uploaded or the ID is retired
     */
    Optional<StoredProof> find(ProofId proof);

    /**
     * Reads a stored proof.
     *
     * <p>Built in M5.
     *
     * @param proof the proof's ID
     * @return the stored file, or empty when none was uploaded or the ID is retired
     */
    Optional<ProofUpload> open(ProofId proof);

    /**
     * Deletes every file stored before a cutoff (NFR-184, ADR-075). Each deleted file's ID keeps its
     * binding to its uploader and kind, as a tombstone: {@link #store} refuses the ID from then on,
     * and {@link #find} and {@link #open} answer empty for it.
     *
     * <p>Built in M8.
     *
     * @param cutoff the cutoff, from {@code RetentionPeriods.proofCutoff}
     * @return how many files were deleted
     */
    int deleteOlderThan(Instant cutoff);

    /**
     * What the store knows of a proof besides its file.
     *
     * <p>Built in M5.
     *
     * @param uploader the account that uploaded it
     * @param kind the kind it was uploaded as
     */
    record StoredProof(AccountId uploader, ProofKind kind) {

        /** Checks both parts are present. */
        public StoredProof {
            Objects.requireNonNull(uploader, "uploader");
            Objects.requireNonNull(kind, "kind");
        }
    }
}
