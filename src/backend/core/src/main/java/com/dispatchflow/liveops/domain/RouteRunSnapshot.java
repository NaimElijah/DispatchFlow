package com.dispatchflow.liveops.domain;

import com.dispatchflow.liveops.events.RunStartCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A route run's stored state: what {@link RouteRun#snapshot()} returns and {@link RouteRun#restore}
 * rebuilds from.
 *
 * @param route the route, which keys the run
 * @param version the version it was loaded at
 * @param startedBy the driver whose report started it
 * @param cause what started it
 * @param startedAt when it started, the start report's corrected time
 * @param status started or finished
 * @param confirmed the last revision the driver confirmed seeing, if any (FR-97)
 */
public record RouteRunSnapshot(
        RouteId route,
        Version version,
        AccountId startedBy,
        RunStartCause cause,
        Instant startedAt,
        RunStatus status,
        Optional<RevisionNo> confirmed) {

    /** Checks every field is present. */
    public RouteRunSnapshot {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(startedBy, "startedBy");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(confirmed, "confirmed");
    }
}
