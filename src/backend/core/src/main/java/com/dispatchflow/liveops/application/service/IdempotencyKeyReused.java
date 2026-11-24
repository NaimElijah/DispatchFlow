package com.dispatchflow.liveops.application.service;

import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import java.util.Objects;

/**
 * A phone sent a request key it already used with a different body, or for another kind of call,
 * whose stored {@code RecordedReport} case doesn't match this one, so the request is refused
 * (NFR-130, ADR-087). It is not a domain refusal, so it is no {@code DomainError}: the web adapter
 * answers it with 409 and {@link #code()}. The message names the key only, never the body.
 *
 * <p>To Be Implemented.
 */
public final class IdempotencyKeyReused extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 409 status (ADR-087). */
    public static final String CODE = "IDEMPOTENCY_KEY_REUSED";

    private final IdempotencyKey key;

    /**
     * Creates the error.
     *
     * @param key the reused request key
     */
    public IdempotencyKeyReused(IdempotencyKey key) {
        super("request key " + Objects.requireNonNull(key, "key").value() + " was used with another body");
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
    public IdempotencyKey key() {
        return key;
    }
}
