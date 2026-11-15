package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A route's progress, as the dispatcher sees it: stops done out of all stops, the delay against the
 * plan, the next stop and when the driver last reported (FR-158), and whether the driver confirmed
 * the route's last change (FR-250).
 *
 * <p>Built in M5.
 *
 * @param route the route
 * @param date its delivery date
 * @param driver its current driver
 * @param run where its run stands (FR-244)
 * @param stopsDone how many of its stops have an outcome
 * @param stopsTotal how many stops it has
 * @param delay how far the next stop's projected arrival is behind its planned one, or empty when no
 *     stop is left or none is timed
 * @param nextStop the first stop in planned order with no outcome, or empty when none is left
 * @param nextArrival the next stop's projected arrival (FR-254)
 * @param lastReportAt when the driver's last report on the route happened, or empty when none
 *     arrived
 * @param changedIn the revision that last changed the route, 1 or more
 * @param confirmed the last revision the driver confirmed, or empty when none (FR-97)
 */
public record RouteProgressView(
        RouteId route,
        DeliveryDate date,
        AccountId driver,
        RunState run,
        int stopsDone,
        int stopsTotal,
        Optional<Duration> delay,
        Optional<StopId> nextStop,
        Optional<Instant> nextArrival,
        Optional<Instant> lastReportAt,
        RevisionNo changedIn,
        Optional<RevisionNo> confirmed) {

    /** Checks every part is present, possibly empty. */
    public RouteProgressView {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(nextStop, "nextStop");
        Objects.requireNonNull(nextArrival, "nextArrival");
        Objects.requireNonNull(lastReportAt, "lastReportAt");
        Objects.requireNonNull(changedIn, "changedIn");
        Objects.requireNonNull(confirmed, "confirmed");
    }
}
