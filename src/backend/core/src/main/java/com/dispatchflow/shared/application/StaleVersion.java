package com.dispatchflow.shared.application;

import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;
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
 * <p>Two checks throw it (ADR-126): {@link #requireCurrent}, in the use case, against the version
 * the caller read; and the persistence adapter's save, when another transaction saved the
 * aggregate after this one loaded it. A retry re-runs the whole use case in a new transaction.
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
        super(message(kind, id));
        this.kind = kind;
        this.id = id;
    }

    /**
     * Creates the error, keeping the optimistic-lock failure that showed it.
     *
     * @param kind what was changed, such as {@code Plan}
     * @param id the changed record's ID
     * @param cause the persistence provider's conflict, whose message names the entity and its key
     *     only
     */
    public StaleVersion(String kind, UUID id, Throwable cause) {
        super(message(kind, id), Objects.requireNonNull(cause, "cause"));
        this.kind = kind;
        this.id = id;
    }

    private static String message(String kind, UUID id) {
        return Objects.requireNonNull(kind, "kind") + " " + Objects.requireNonNull(id, "id")
                + " changed since it was read";
    }

    /**
     * Refuses a change made against a version that is no longer current. A use case calls it
     * straight after loading, with the version the caller last read: from {@code If-Match}, or from
     * the body when a proposal is accepted (ADR-121, P31). JPA's {@code @Version} only guards the
     * time between this transaction's load and its commit, so this check covers the time before.
     *
     * @param kind what is being changed, such as {@code Plan}
     * @param id the changed record's ID
     * @param loaded the version the use case just loaded
     * @param expected the version the caller last read
     * @throws StaleVersion when the two differ
     */
    public static void requireCurrent(String kind, UUID id, Version loaded, Version expected) {
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(expected, "expected");
        if (!loaded.equals(expected)) {
            throw new StaleVersion(kind, id);
        }
    }

    /**
     * The same check, for an aggregate that may not be stored yet: a singleton or a date's roster
     * whose first change the caller makes with {@code If-None-Match: *} (ADR-126).
     *
     * @param kind what is being changed, such as {@code CompanyProfile}
     * @param id the changed record's ID
     * @param loaded the version the use case just loaded, or empty when it was never saved
     * @param expected the version the caller last read, or empty when it read none and sent {@code
     *     If-None-Match: *}
     * @throws StaleVersion when the two differ: one is empty and the other isn't, or both are there
     *     and unequal
     */
    public static void requireCurrent(String kind, UUID id, Optional<Version> loaded, Optional<Version> expected) {
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(expected, "expected");
        if (!loaded.equals(expected)) {
            throw new StaleVersion(kind, id);
        }
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
