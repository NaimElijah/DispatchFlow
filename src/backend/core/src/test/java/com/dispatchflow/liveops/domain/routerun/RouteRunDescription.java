package com.dispatchflow.liveops.domain.routerun;

import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.events.RunStartCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.Optional;

/**
 * What a test sees of a {@link RouteRun}, read through its getters: a value that compares by every
 * part. Tests compare a run before and after a refused command, or after a load with what was
 * added, through it. It leaves the version out, which tests check on their own.
 *
 * @param route the route, which keys the run
 * @param startedBy the driver whose report started it
 * @param cause what started it
 * @param startedAt when it started
 * @param status started or finished
 * @param confirmed the last revision the driver confirmed seeing, if any
 */
public record RouteRunDescription(
        RouteId route,
        AccountId startedBy,
        RunStartCause cause,
        Instant startedAt,
        RunStatus status,
        Optional<RevisionNo> confirmed) {

    /**
     * Describes a run.
     *
     * @param run the run
     * @return what its getters give
     */
    public static RouteRunDescription of(RouteRun run) {
        return new RouteRunDescription(
                run.route(), run.startedBy(), run.cause(), run.startedAt(), run.status(), run.confirmed());
    }
}
