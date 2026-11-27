package com.dispatchflow.liveops.application.port.out.proof;

import com.dispatchflow.shared.application.ApplicationRefusal;

/**
 * An uploaded proof's header gives pixel dimensions above the store's cap, so it is refused before
 * anything decodes it: a file within NFR-37's 5 MB can still unpack into a bitmap that exhausts the
 * memory (NFR-205). It is not a domain refusal, so it is no {@code DomainError}: the web adapter
 * answers it with 422 and {@link #code()}. The message names no dimension, so the cap is not
 * probed through it.
 *
 * <p>To Be Implemented.
 */
public final class ProofImageTooLarge extends RuntimeException implements ApplicationRefusal {

    /** The stable code the web adapter answers with, beside the 422 status. */
    public static final String CODE = "PROOF_IMAGE_TOO_LARGE";

    /** Creates the error. */
    public ProofImageTooLarge() {
        super("the proof image's dimensions are above the cap");
    }

    /**
     * The stable code.
     *
     * @return {@value #CODE}
     */
    @Override
    public String code() {
        return CODE;
    }
}
