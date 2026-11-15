package com.dispatchflow.liveops.application.port.out;

import java.util.Objects;

/**
 * The hash of a phone request's method, path and body, which the web adapter computes, so a key
 * reused for another call, another route or another body is told apart from a retry (ADR-087,
 * NFR-130). It is a hash, never the body, so it holds no
 * personal data.
 *
 * <p>Built in M8.
 *
 * @param value the hash, in the adapter's encoding
 */
public record RequestDigest(String value) {

    /** Checks the hash is present. */
    public RequestDigest {
        Objects.requireNonNull(value, "value");
    }
}
