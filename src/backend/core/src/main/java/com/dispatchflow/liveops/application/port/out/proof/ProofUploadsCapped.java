package com.dispatchflow.liveops.application.port.out.proof;

/**
 * The uploader already holds as many uploads that no report names as the store's cap allows, so the
 * store refuses another until the store's sweep, or a report naming one, frees a place (ADR-087). It
 * is not a domain refusal, so it is no {@code DomainError}: the web adapter answers it with 429,
 * {@code Retry-After} and {@link #code()}, so ADR-087's sender waits and retries the upload instead
 * of marking its row refused.
 *
 * <p>Built in M5.
 */
public final class ProofUploadsCapped extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 429 status. */
    public static final String CODE = "PROOF_UPLOADS_CAPPED";

    /** Creates the error. */
    public ProofUploadsCapped() {
        super("the uploader holds as many unnamed proof uploads as the cap allows");
    }

    /**
     * The stable code.
     *
     * @return {@value #CODE}
     */
    public String code() {
        return CODE;
    }
}
