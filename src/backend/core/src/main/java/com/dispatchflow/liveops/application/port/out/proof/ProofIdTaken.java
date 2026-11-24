package com.dispatchflow.liveops.application.port.out.proof;

import com.dispatchflow.liveops.domain.proof.ProofId;
import java.util.Objects;

/**
 * An upload names a proof ID that another account already holds, that its own uploader holds under
 * the other kind, or that is retired because its file was deleted, so the upload is refused and the
 * stored file, if any, stays as it was (FR-367, BR-02, ADR-087). It is not a domain refusal, so it
 * is no {@code DomainError}: the web adapter answers it with 409 and {@link #code()}. The message
 * names the ID only, never the uploader.
 *
 * <p>To Be Implemented.
 */
public final class ProofIdTaken extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 409 status. */
    public static final String CODE = "PROOF_ID_TAKEN";

    private final ProofId proof;

    /**
     * Creates the error.
     *
     * @param proof the ID the upload named
     */
    public ProofIdTaken(ProofId proof) {
        super("proof " + Objects.requireNonNull(proof, "proof").value() + " is held by another upload or retired");
        this.proof = proof;
    }

    /**
     * The stable code.
     *
     * @return {@value #CODE}
     */
    public String code() {
        return CODE;
    }

    /**
     * The ID the upload named, for the problem's {@code params}.
     *
     * @return the proof's ID
     */
    public ProofId proof() {
        return proof;
    }
}
