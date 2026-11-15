package com.dispatchflow.shared.application;

import java.util.Objects;
import java.util.UUID;

/**
 * A change was made against a version of an aggregate that is no longer current, so optimistic
 * locking refuses it (BR-76, ADR-057). It is not a domain refusal, so it is no {@code DomainError}.
 * The web adapter answers a call that sent {@code If-Match}, a use case's {@code Version expected},
 * with 412 and {@link #code()}, through {@code ETag}. A call that sent no version never saw the
 * version it lost to, so the adapter retries it at once, up to three times, and then answers 503
 * with {@code Retry-After} (ADR-121), as a listener adapter retries (DL §11). The message names the
 * kind and the ID only, never personal data (NFR-19).
 *
 * <p>Built in M0.
 */
public final class StaleVersion extends RuntimeException {

    private final String kind;
    private final UUID id;

    /** The stable code the web adapter answers with, beside the 412 status. */
    public static final String CODE = "STALE_VERSION";

    /**
     * Creates the error.
     *
     * @param kind what was changed, such as {@code Plan}
     * @param id the changed record's ID
     */
    public StaleVersion(String kind, UUID id) {
        super(Objects.requireNonNull(kind, "kind") + " " + Objects.requireNonNull(id, "id")
                + " changed since it was read");
        this.kind = kind;
        this.id = id;
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
     * What was changed, for the problem's {@code params}.
     *
     * @return the kind, such as {@code Plan}
     */
    public String kind() {
        return kind;
    }

    /**
     * The record's ID, for the problem's {@code params}.
     *
     * @return the ID
     */
    public UUID id() {
        return id;
    }
}
