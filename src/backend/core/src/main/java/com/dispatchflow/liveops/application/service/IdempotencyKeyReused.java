package com.dispatchflow.liveops.application.service;

import java.util.Objects;
import java.util.UUID;

/**
 * A phone sent a request key it already used with a different body, so the request is refused
 * (NFR-130, ADR-087). It is not a domain refusal, so it is no {@code DomainError}: the web adapter
 * answers it with 409 and {@link #code()}. The message names the key only, never the body.
 *
 * <p>Built in M8.
 */
public final class IdempotencyKeyReused extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 409 status (ADR-087). */
    public static final String CODE = "IDEMPOTENCY_KEY_REUSED";

    private final UUID key;

    /**
     * Creates the error.
     *
     * @param key the reused request key
     */
    public IdempotencyKeyReused(UUID key) {
        super("request key " + Objects.requireNonNull(key, "key") + " was used with another body");
        this.key = key;
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
     * The reused key, for the problem's {@code params}.
     *
     * @return the key
     */
    public UUID key() {
        return key;
    }
}
