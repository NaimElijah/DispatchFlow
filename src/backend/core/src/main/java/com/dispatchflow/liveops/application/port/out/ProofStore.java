package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.delivery.ProofId;
import java.time.Instant;
import java.util.Optional;

/**
 * Where proof-of-delivery files are kept: the VM's disk, served only through authorized calls
 * (ADR-027, NFR-38, TS §6.4). A file is deleted 90 days after it was stored, and the delivery keeps
 * the proof's kind (NFR-184, ADR-107).
 *
 * <p>Built in M5.
 */
public interface ProofStore {

    /**
     * Stores an uploaded proof under the phone's outbox row ID. The adapter re-encodes the image and
     * strips its metadata before it writes (NFR-205). Storing an ID that is stored already changes
     * nothing, so the phone's retried upload is safe (ADR-087).
     *
     * <p>Built in M5.
     *
     * @param proof the proof's ID
     * @param upload the uploaded image
     */
    void store(ProofId proof, ProofUpload upload);

    /**
     * Reads a stored proof.
     *
     * <p>Built in M5.
     *
     * @param proof the proof's ID
     * @return the stored file, or empty when none was uploaded or retention deleted it
     */
    Optional<ProofUpload> open(ProofId proof);

    /**
     * Deletes every file stored before a cutoff (NFR-184, ADR-075).
     *
     * <p>Built in M8.
     *
     * @param cutoff the cutoff, from {@code RetentionPeriods.proofCutoff}
     * @return how many files were deleted
     */
    int deleteOlderThan(Instant cutoff);
}
