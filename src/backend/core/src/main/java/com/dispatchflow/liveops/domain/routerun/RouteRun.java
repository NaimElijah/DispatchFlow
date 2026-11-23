package com.dispatchflow.liveops.domain.routerun;

import com.dispatchflow.liveops.domain.DriverReport;
import com.dispatchflow.liveops.domain.LiveOpsError.NotYourRoute;
import com.dispatchflow.liveops.domain.LiveOpsError.RouteAlreadyFinished;
import com.dispatchflow.liveops.domain.LiveOpsError.RunAlreadyStarted;
import com.dispatchflow.liveops.domain.LiveOpsError.TrackingClosed;
import com.dispatchflow.liveops.domain.LiveOpsError.UnknownRevision;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.TrackingRefusal;
import com.dispatchflow.liveops.events.RouteStarted;
import com.dispatchflow.liveops.events.RunStartCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A route being driven (DL §4.5, §17), keyed by its {@link RouteId} and created when it starts: by
 * the driver's start, by their first stop report (FR-327), or when a dispatcher confirms a held
 * report that was its first (DL §6.2). A record made on a driver's behalf starts none, because it
 * carries no {@link DriverReport} (ADR-107); nor does a start or report from a driver the route was
 * handed away from (ADR-114). It keeps when it started and finished, which bound the positions it
 * keeps (FR-328), and the last revision the driver confirmed seeing (FR-97).
 *
 * <p>A late report after *finished* is accepted by {@link
 * com.dispatchflow.liveops.domain.delivery.Delivery} and changes no run (ADR-107). Every method
 * checks all its rules before it changes anything.
 */
public final class RouteRun extends AggregateRoot {

    private final RouteId route;
    private final AccountId startedBy;
    private final RunStartCause cause;
    private final Instant startedAt;
    private RunStatus status;
    private Optional<RevisionNo> confirmed;

    private RouteRun(RouteRunSnapshot stored) {
        super(stored.version());
        this.route = stored.route();
        this.startedBy = stored.startedBy();
        this.cause = stored.cause();
        this.startedAt = stored.startedAt();
        this.status = stored.status();
        this.confirmed = stored.confirmed();
    }

    /**
     * Starts a route that has no run, and raises {@link RouteStarted} (FR-68, FR-327). Only the
     * route's driver at the report's time who still drives it may start it, so a driver the route
     * was handed away from can't start the new driver's run, and send its recipients *out for
     * delivery*, with a start timed before the hand-over (FR-101, FR-351, ADR-114).
     *
     * @param route the route
     * @param report the driver's start or first report, whose corrected time is the start
     * @param routeDriver the route's driver in the revision in force at the report's corrected
     *     time, from Planning; for a first stop report, the driver its {@link
     *     com.dispatchflow.liveops.domain.delivery.Entitlement} names
     * @param currentDriver the route's driver in the current revision
     * @param cause what started it
     * @param now when the server records it
     * @return the run, started, never saved
     * @throws RuleViolation with {@link NotYourRoute} when the report's driver is not both {@code
     *     routeDriver} and {@code currentDriver}, as after a hand-over (BR-02, ADR-114)
     */
    public static RouteRun start(
            RouteId route,
            DriverReport report,
            AccountId routeDriver,
            AccountId currentDriver,
            RunStartCause cause,
            Instant now) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(routeDriver, "routeDriver");
        Objects.requireNonNull(currentDriver, "currentDriver");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(now, "now");
        refuseUnlessDrivingThenAndNow(route, report, routeDriver, currentDriver);
        Instant startedAt = report.time().corrected();
        RouteRun run = new RouteRun(new RouteRunSnapshot(
                route, null, report.driver(), cause, startedAt, new RunStatus.Started(), Optional.empty()));
        run.raise(
                RouteStarted.class,
                route.value(),
                eventId -> new RouteStarted(eventId, now, route, report.driver(), startedAt, cause));
        return run;
    }

    /**
     * Rebuilds a stored run; only the persistence adapter calls it (DL §3.5).
     *
     * @param stored the stored state
     * @return the run at the stored version
     * @throws StoredStateInvalid when a finished run names a pending stop twice
     */
    public static RouteRun restore(RouteRunSnapshot stored) {
        Optional<String> broken = brokenBy(stored);
        if (broken.isPresent()) {
            throw new StoredStateInvalid(RouteRun.class, broken.get());
        }
        return new RouteRun(stored);
    }

    /**
     * The run's state, for the repository to store.
     *
     * @return the stored form
     */
    public RouteRunSnapshot snapshot() {
        return snapshotOf(status, confirmed);
    }

    /**
     * The run's key.
     *
     * @return the route
     */
    public RouteId route() {
        return route;
    }

    /**
     * Whether the driver has finished the route (FR-244).
     *
     * @return {@code true} once finished
     */
    public boolean isFinished() {
        return status instanceof RunStatus.Finished;
    }

    /**
     * The last revision the driver confirmed seeing (FR-97, FR-250).
     *
     * @return the revision, or empty when none was confirmed
     */
    public Optional<RevisionNo> confirmed() {
        return confirmed;
    }

    /**
     * A driver's start on a route that already has a run; a run starts once (FR-68).
     *
     * @param report the driver's start
     * @param routeDriver the route's driver in the revision in force at the report's corrected time
     * @param currentDriver the route's driver in the current revision
     * @param now when the server received it
     * @throws RuleViolation with {@link NotYourRoute} when the report's driver is not both {@code
     *     routeDriver} and {@code currentDriver} (BR-02, ADR-114), else {@link RouteAlreadyFinished}
     *     when the run has finished, else {@link RunAlreadyStarted}
     */
    public void start(DriverReport report, AccountId routeDriver, AccountId currentDriver, Instant now) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(routeDriver, "routeDriver");
        Objects.requireNonNull(currentDriver, "currentDriver");
        Objects.requireNonNull(now, "now");
        refuseUnlessDrivingThenAndNow(route, report, routeDriver, currentDriver);
        refuseIfFinished();
        throw new RuleViolation(new RunAlreadyStarted(route));
    }

    /**
     * Finishes the route, perhaps with stops pending, which the use case alerts on (FR-68, FR-329).
     * Only the route's driver at the report's time who still drives it may finish it, so a driver
     * the route was handed away from can't end the new driver's run with a finish timed before the
     * hand-over (FR-328, ADR-114).
     *
     * @param report the driver's finish, whose corrected time is the finish
     * @param routeDriver the route's driver in the revision in force at the report's corrected time
     * @param currentDriver the route's driver in the current revision
     * @param pending the stops with no outcome, in the route's order
     * @throws RuleViolation with {@link NotYourRoute} when the report's driver is not both {@code
     *     routeDriver} and {@code currentDriver} (BR-02, ADR-114), else {@link RouteAlreadyFinished}
     *     when the run has finished
     */
    public void finish(DriverReport report, AccountId routeDriver, AccountId currentDriver, List<StopId> pending) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(routeDriver, "routeDriver");
        Objects.requireNonNull(currentDriver, "currentDriver");
        Objects.requireNonNull(pending, "pending");
        refuseUnlessDrivingThenAndNow(route, report, routeDriver, currentDriver);
        refuseIfFinished();
        commit(new RunStatus.Finished(report.time().corrected(), report.driver(), pending), confirmed);
    }

    /**
     * Records that the driver saw a route change: *Seen* (FR-97). The confirmed revision never goes
     * back (FR-250): an older or equal one is ignored. A finished run takes it too. Only the route's
     * current driver confirms, so a driver the route was handed away from can't hide the new
     * driver's unconfirmed change (FR-250, ADR-114).
     *
     * @param driver the account that sent *Seen*
     * @param currentDriver the route's driver in the current revision
     * @param seen the revision the phone showed
     * @param current the plan's current revision
     * @throws RuleViolation with {@link NotYourRoute} when {@code driver} is not {@code
     *     currentDriver} (BR-02, ADR-114), else {@link UnknownRevision} when {@code seen} is past
     *     {@code current} (ADR-107)
     */
    public void acknowledge(AccountId driver, AccountId currentDriver, RevisionNo seen, RevisionNo current) {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(currentDriver, "currentDriver");
        Objects.requireNonNull(seen, "seen");
        Objects.requireNonNull(current, "current");
        refuseIfNotTheDriver(route, driver, currentDriver);
        if (seen.isAfter(current)) {
            throw new RuleViolation(new UnknownRevision(route, seen, current));
        }
        if (confirmed.filter(stored -> !seen.isAfter(stored)).isPresent()) {
            return;
        }
        commit(status, Optional.of(seen));
    }

    /**
     * Whether a route change waits for the driver's confirmation past its deadline, so the
     * dispatcher is alerted (FR-97, FR-157, ADR-107). Only a run in progress counts: a revision that
     * took effect before the run started counts as seen, because the driver started on the route
     * the phone had just read again (FR-347).
     *
     * @param changedIn the revision that last changed this route
     * @param changedAt when that revision took effect
     * @param now the time to judge at
     * @return {@code true} when the run is in progress, the revision took effect at or after its
     *     start, the driver hasn't confirmed it, and its {@link ConfirmationDeadline} has passed
     */
    public boolean isConfirmationOverdue(RevisionNo changedIn, Instant changedAt, Instant now) {
        Objects.requireNonNull(changedIn, "changedIn");
        Objects.requireNonNull(changedAt, "changedAt");
        Objects.requireNonNull(now, "now");
        return status instanceof RunStatus.Started
                && !changedAt.isBefore(startedAt)
                && confirmed.filter(stored -> !changedIn.isAfter(stored)).isEmpty()
                && ConfirmationDeadline.after(changedAt).isPassed(now);
    }

    /**
     * Checks a position may be kept (FR-328, NFR-30).
     *
     * @param at the position's time
     * @param standing whether the sender drives the route now, is available, and the shift end in
     *     force for them (FR-394)
     * @throws RuleViolation with {@link TrackingClosed}, whose reason is, in this order, {@link
     *     TrackingRefusal#NOT_THE_CURRENT_DRIVER}, {@link TrackingRefusal#DRIVER_UNAVAILABLE} or
     *     {@link TrackingRefusal#OUTSIDE_WINDOW}
     */
    public void checkPositionAt(Instant at, TrackingStanding standing) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(standing, "standing");
        refusalAt(at, standing).ifPresent(reason -> {
            throw new RuleViolation(new TrackingClosed(route, reason));
        });
    }

    /**
     * The same rule as {@link #checkPositionAt}, as a query for filtering a batch of positions (DL
     * §3.2, §3.10).
     *
     * @param at the position's time
     * @param standing whether the sender drives the route now, and is available
     * @return {@code true} when the position is kept
     */
    public boolean acceptsPositionAt(Instant at, TrackingStanding standing) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(standing, "standing");
        return refusalAt(at, standing).isEmpty();
    }

    private Optional<TrackingRefusal> refusalAt(Instant at, TrackingStanding standing) {
        if (!standing.currentDriver()) {
            return Optional.of(TrackingRefusal.NOT_THE_CURRENT_DRIVER);
        }
        if (!standing.available()) {
            return Optional.of(TrackingRefusal.DRIVER_UNAVAILABLE);
        }
        Optional<Instant> finishedAt =
                status instanceof RunStatus.Finished finished ? Optional.of(finished.at()) : Optional.empty();
        return TrackingWindow.of(startedAt, finishedAt, standing.shiftEnd()).admits(at)
                ? Optional.empty()
                : Optional.of(TrackingRefusal.OUTSIDE_WINDOW);
    }

    /**
     * Only the driver who drove the route at the report's time and still drives it may start or
     * finish it, and only the current driver may confirm a change, so a driver the route was handed
     * away from can't, even by backdating (BR-02, ADR-114). It comes first, so such a report learns
     * nothing of the run's state.
     */
    private static void refuseUnlessDrivingThenAndNow(
            RouteId route, DriverReport report, AccountId routeDriver, AccountId currentDriver) {
        refuseIfNotTheDriver(route, report.driver(), routeDriver);
        refuseIfNotTheDriver(route, report.driver(), currentDriver);
    }

    private static void refuseIfNotTheDriver(RouteId route, AccountId sender, AccountId routeDriver) {
        if (!sender.equals(routeDriver)) {
            throw new RuleViolation(new NotYourRoute(route));
        }
    }

    private void refuseIfFinished() {
        if (status instanceof RunStatus.Finished) {
            throw new RuleViolation(new RouteAlreadyFinished(route));
        }
    }

    /**
     * Replaces the state with the one a command built, after checking it holds every at-rest
     * invariant; a broken one here is a bug in the command, not a refusal.
     */
    private void commit(RunStatus nextStatus, Optional<RevisionNo> nextConfirmed) {
        Optional<String> broken = brokenBy(snapshotOf(nextStatus, nextConfirmed));
        if (broken.isPresent()) {
            throw new AssertionError("a route run command would leave a state that breaks: " + broken.get());
        }
        status = nextStatus;
        confirmed = nextConfirmed;
    }

    private RouteRunSnapshot snapshotOf(RunStatus nextStatus, Optional<RevisionNo> nextConfirmed) {
        return new RouteRunSnapshot(
                route, savedVersion().orElse(null), startedBy, cause, startedAt, nextStatus, nextConfirmed);
    }

    private static Optional<String> brokenBy(RouteRunSnapshot state) {
        if (state.status() instanceof RunStatus.Finished finished
                && new HashSet<>(finished.pending()).size()
                        != finished.pending().size()) {
            return Optional.of("a finished run names each pending stop once (FR-329)");
        }
        return Optional.empty();
    }
}
