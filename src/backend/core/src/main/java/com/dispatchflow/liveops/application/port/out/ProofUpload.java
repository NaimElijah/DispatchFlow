package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * A proof of delivery's file: the signature or photo the phone uploaded, and the stored file a
 * dispatcher views (FR-30, FR-105, NFR-204). A photo of a door or a signature identifies a person,
 * so it prints redacted.
 *
 * <p>Built in M5.
 *
 * @param mediaType the image's format
 * @param content the image's bytes, copied when the record is made
 */
public record ProofUpload(ProofMediaType mediaType, byte[] content) implements PersonalData {

    /** Checks both parts are present, and copies the bytes. */
    public ProofUpload {
        Objects.requireNonNull(mediaType, "mediaType");
        content = Objects.requireNonNull(content, "content").clone();
    }

    /**
     * A form that hides the image.
     *
     * @return {@code ProofUpload[redacted]}
     */
    @Override
    public String toString() {
        return "ProofUpload[redacted]";
    }
}
