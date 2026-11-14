package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * An alert's stored state: what {@link Alert#snapshot()} returns and {@link Alert#restore} rebuilds
 * from. The dedupe key is not stored here: it is derived from the subject, and the repository stores
 * it for its unique index.
 *
 * @param id the alert
 * @param version the version it was loaded at
 * @param subject what it is about
 * @param raisedAt when it was raised
 * @param state open or acknowledged
 */
public record AlertSnapshot(AlertId id, Version version, AlertSubject subject, Instant raisedAt, AlertState state) {

    /** Checks every field is present. */
    public AlertSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(raisedAt, "raisedAt");
        Objects.requireNonNull(state, "state");
    }
}
