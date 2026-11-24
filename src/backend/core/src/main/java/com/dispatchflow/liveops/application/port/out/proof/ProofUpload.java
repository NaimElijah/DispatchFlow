package com.dispatchflow.liveops.application.port.out.proof;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * A proof of delivery's file: the signature or photo the phone uploaded, and the stored file a
 * dispatcher views (FR-30, FR-105, NFR-204). A photo of a door or a signature identifies a person,
 * so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * <p>A class, not a record: a record holding an array compares it by identity and hands out
 * the array itself.
 */
public final class ProofUpload implements PersonalData {

    private final ProofMediaType mediaType;
    private final byte[] content;

    /**
     * Creates the upload, keeping its own copy of the bytes.
     *
     * @param mediaType the image's declared type
     * @param content the image's bytes
     */
    public ProofUpload(ProofMediaType mediaType, byte[] content) {
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.content = Objects.requireNonNull(content, "content").clone();
    }

    /**
     * The image's declared type.
     *
     * @return the media type
     */
    public ProofMediaType mediaType() {
        return mediaType;
    }

    /**
     * The image's bytes, as a copy, so no caller can change the upload.
     *
     * @return a copy of the bytes
     */
    public byte[] content() {
        return content.clone();
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
