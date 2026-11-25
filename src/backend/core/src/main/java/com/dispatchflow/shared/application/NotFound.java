package com.dispatchflow.shared.application;

import java.util.Objects;
import java.util.UUID;

/**
 * A use case was asked about a record that doesn't exist. It is not a domain refusal, so it is no
 * {@code DomainError}: the web adapter answers it with 404 and {@link #code()}. The message names
 * the kind and the ID only, never personal data (NFR-19).
 */
public final class NotFound extends RuntimeException {

    private final String kind;
    private final UUID id;

    /** The stable code the web adapter answers with, beside the 404 status. */
    public static final String CODE = "NOT_FOUND";

    /**
     * Creates the error.
     *
     * @param kind what was looked for, such as {@code Plan}
     * @param id the ID that names no record
     */
    public NotFound(String kind, UUID id) {
        super(Objects.requireNonNull(kind, "kind") + " " + Objects.requireNonNull(id, "id") + " does not exist");
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
     * What was looked for, for the problem's {@code params}.
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
