package com.dispatchflow.liveops.application.port.out.proof;

import com.dispatchflow.shared.application.ApplicationRefusal;

/**
 * An uploaded proof starts as the declared JPEG or PNG, but the declared format's reader can't
 * decode it, so it is refused and nothing is stored: the store re-encodes every proof before it
 * writes (NFR-205), and a file it can't decode can't be re-encoded. It is not a domain refusal, so
 * it is no {@code DomainError}: the web adapter answers it with 422 and {@link #code()}. The message
 * names no detail of the decoder's failure, so the decoder is not probed through it.
 *
 * <p>To Be Implemented.
 */
public final class ProofImageUnreadable extends RuntimeException implements ApplicationRefusal {

    /** The stable code the web adapter answers with, beside the 422 status. */
    public static final String CODE = "PROOF_IMAGE_UNREADABLE";

    /** Creates the error. */
    public ProofImageUnreadable() {
        super("the proof image can't be decoded");
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
