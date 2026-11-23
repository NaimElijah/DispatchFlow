package com.dispatchflow.liveops.domain.proof;

import java.util.Objects;
import java.util.UUID;

/**
 * Names an uploaded proof of delivery. The phone uploads the proof first, under its outbox row's ID,
 * and then sends the report that names it (FR-30, ADR-087). A local ID: only Live Operations names
 * it.
 *
 * @param value the UUID
 */
public record ProofId(UUID value) {

    /** Checks the UUID is present. */
    public ProofId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static ProofId of(UUID value) {
        return new ProofId(value);
    }
}
