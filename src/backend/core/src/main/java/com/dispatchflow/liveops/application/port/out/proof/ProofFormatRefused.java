package com.dispatchflow.liveops.application.port.out.proof;

import java.util.Objects;

/**
 * An uploaded proof's leading bytes are not those of the JPEG or PNG it was declared as, so it is
 * refused before any reader touches it: only JPEG and PNG are accepted (NFR-37), and a file whose
 * content doesn't match its declared type never reaches a decoder for another format (NFR-205). It
 * is not a domain refusal, so it is no {@code DomainError}: the web adapter answers it with 422 and
 * {@link #code()}, like its siblings, so the phone's outbox marks the row refused (ADR-087). The
 * message names the declared type only, never the content.
 *
 * <p>Built in M5.
 */
public final class ProofFormatRefused extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 422 status. */
    public static final String CODE = "PROOF_FORMAT_REFUSED";

    private final ProofMediaType declared;

    /**
     * Creates the error.
     *
     * @param declared the type the upload declared, which its content doesn't match
     */
    public ProofFormatRefused(ProofMediaType declared) {
        super("the proof's content is not the declared " + Objects.requireNonNull(declared, "declared"));
        this.declared = declared;
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
     * The type the upload declared, for the problem's {@code params}.
     *
     * @return the declared type
     */
    public ProofMediaType declared() {
        return declared;
    }
}
