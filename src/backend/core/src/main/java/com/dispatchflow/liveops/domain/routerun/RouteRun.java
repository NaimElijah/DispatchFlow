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
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
 *
 * <p>It is mapped for JPA (ADR-126): its key is its route's UUID (ADR-127 decision 5), its {@link
 * RunStatus} a kind plus the finish's time and driver, with the pending stops an ordered element
 * collection, and its confirmed revision a nullable column. Every command changes those in place
 * and ends by checking the invariants at rest that the load check runs. The route's delivery date,
 * which the run does not hold, is kept beside it by the persistence adapter.
 */
@Entity
@Table(schema = "liveops", name = "route_run")
public class RouteRun extends AggregateRoot {

    /** Which {@link RunStatus} a run is in. */
    private enum StatusKind {
        STARTED,
        FINISHED
    }

    @Id
    private UUID id;

    private AccountId startedBy;

    @Enumerated(EnumType.STRING)
    private RunStartCause cause;

    private Instant startedAt;

    @Enumerated(EnumType.STRING)
    private StatusKind status;

    private @Nullable Instant finishedAt;

    private @Nullable AccountId finishedBy;

    @ElementCollection
    @CollectionTable(
            schema = "liveops",
            name = "route_run_pending_stop",
            joinColumns = @JoinColumn(name = "route_run_id"))
    @OrderColumn(name = "position", nullable = false)
    @Column(name = "stop")
    private List<StopId> pendingStops = new ArrayList<>();

    private @Nullable RevisionNo confirmed;

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected RouteRun() {}

    private RouteRun(RouteId route, AccountId startedBy, RunStartCause cause, Instant startedAt) {
        this.id = route.value();
        this.startedBy = startedBy;
        this.cause = cause;
        this.startedAt = startedAt;
        this.status = StatusKind.STARTED;
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
        RouteRun run = new RouteRun(route, report.driver(), cause, startedAt);
        run.raise(
                RouteStarted.class,
                route.value(),
                eventId -> new RouteStarted(eventId, now, route, report.driver(), startedAt, cause));
        return run;
    }

    /**
     * Checks the run as Hibernate loaded it against the invariants that hold at rest (ADR-126
     * decision 8). The pending stops are rows of their own, which no constraint ties to the status.
     *
     * @throws StoredStateInvalid when a stored list has a gap in its positions, a started run has
     *     pending stops, or a finished run names a pending stop twice
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(RouteRun.class, broken);
        });
    }

    /**
     * The run's key.
     *
     * @return the route
     */
    public RouteId route() {
        return RouteId.of(id);
    }

    /**
     * The driver whose report started the run.
     *
     * @return the driver
     */
    public AccountId startedBy() {
        return startedBy;
    }

    /**
     * What started the run (FR-327, DL §6.2).
     *
     * @return the cause
     */
    public RunStartCause cause() {
        return cause;
    }

    /**
     * When the run started: its start report's corrected time.
     *
     * @return the start
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * Where the run stands (FR-244).
     *
     * @return started, or finished with its time, its driver and the stops it left pending
     */
    public RunStatus status() {
        return switch (status) {
            case STARTED -> new RunStatus.Started();
            case FINISHED ->
                new RunStatus.Finished(
                        Objects.requireNonNull(finishedAt, "a finished run has its finish time"),
                        Objects.requireNonNull(finishedBy, "a finished run names its driver"),
                        pendingStops);
        };
    }

    /**
     * Whether the driver has finished the route (FR-244).
     *
     * @return {@code true} once finished
     */
    public boolean isFinished() {
        return status == StatusKind.FINISHED;
    }

    /**
     * The last revision the driver confirmed seeing (FR-97, FR-250).
     *
     * @return the revision, or empty when none was confirmed
     */
    public Optional<RevisionNo> confirmed() {
        return Optional.ofNullable(confirmed);
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
        refuseUnlessDrivingThenAndNow(route(), report, routeDriver, currentDriver);
        refuseIfFinished();
        throw new RuleViolation(new RunAlreadyStarted(route()));
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
        refuseUnlessDrivingThenAndNow(route(), report, routeDriver, currentDriver);
        refuseIfFinished();
        finishAs(new RunStatus.Finished(report.time().corrected(), report.driver(), pending));
        requireInvariants();
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
        refuseIfNotTheDriver(route(), driver, currentDriver);
        if (seen.isAfter(current)) {
            throw new RuleViolation(new UnknownRevision(route(), seen, current));
        }
        if (confirmed().filter(stored -> !seen.isAfter(stored)).isPresent()) {
            return;
        }
        confirmed = seen;
        requireInvariants();
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
        return status == StatusKind.STARTED
                && !changedAt.isBefore(startedAt)
                && confirmed().filter(stored -> !changedIn.isAfter(stored)).isEmpty()
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
            throw new RuleViolation(new TrackingClosed(route(), reason));
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
        return TrackingWindow.of(startedAt, Optional.ofNullable(finishedAt), standing.shiftEnd())
                        .admits(at)
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
        if (isFinished()) {
            throw new RuleViolation(new RouteAlreadyFinished(route()));
        }
    }

    /**
     * Checks the state a command left against the invariants at rest (ADR-126 decision 11). Every
     * rule a caller can break was checked before the command changed anything, so a broken one here
     * is a bug in the command, not a refusal, and the error rolls its transaction back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a route run command left a state that breaks: " + broken);
        });
    }

    /** The one check of the invariants at rest, shared by the load check and every command. */
    private Optional<String> brokenRule() {
        if (StoredLists.anyHasGap(pendingStops)) {
            return Optional.of(StoredLists.GAP);
        }
        if (status == StatusKind.STARTED && !pendingStops.isEmpty()) {
            return Optional.of("only a finished run has pending stops (FR-329)");
        }
        if (status == StatusKind.FINISHED && new HashSet<>(pendingStops).size() != pendingStops.size()) {
            return Optional.of("a finished run names each pending stop once (FR-329)");
        }
        return Optional.empty();
    }

    /** Finishes the run in place: a run finishes once, so a started run's columns are empty. */
    private void finishAs(RunStatus.Finished finished) {
        status = StatusKind.FINISHED;
        finishedAt = finished.at();
        finishedBy = finished.by();
        pendingStops.addAll(finished.pending());
    }
}
