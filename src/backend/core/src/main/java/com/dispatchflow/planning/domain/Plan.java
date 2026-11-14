package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.Layout.Located;
import com.dispatchflow.planning.domain.PlanningError.BreakNotConfirmed;
import com.dispatchflow.planning.domain.PlanningError.DateInPast;
import com.dispatchflow.planning.domain.PlanningError.DateNotOver;
import com.dispatchflow.planning.domain.PlanningError.DriverAlreadyHasRoute;
import com.dispatchflow.planning.domain.PlanningError.DriverNotAvailable;
import com.dispatchflow.planning.domain.PlanningError.NotAnActiveDriver;
import com.dispatchflow.planning.domain.PlanningError.NothingToRedo;
import com.dispatchflow.planning.domain.PlanningError.NothingToUndo;
import com.dispatchflow.planning.domain.PlanningError.OpenViolation;
import com.dispatchflow.planning.domain.PlanningError.OrderAlreadyInPlan;
import com.dispatchflow.planning.domain.PlanningError.OrderChangedSinceSolve;
import com.dispatchflow.planning.domain.PlanningError.OrderNotAccounted;
import com.dispatchflow.planning.domain.PlanningError.OrderNotDropped;
import com.dispatchflow.planning.domain.PlanningError.OrderNotPinned;
import com.dispatchflow.planning.domain.PlanningError.OrderNotRoutable;
import com.dispatchflow.planning.domain.PlanningError.PlanAlreadyExists;
import com.dispatchflow.planning.domain.PlanningError.PlanNotDraftOrReady;
import com.dispatchflow.planning.domain.PlanningError.PlanNotDraftSolvingOrReady;
import com.dispatchflow.planning.domain.PlanningError.PlanNotPublished;
import com.dispatchflow.planning.domain.PlanningError.PlanNotReady;
import com.dispatchflow.planning.domain.PlanningError.PositionOutOfRange;
import com.dispatchflow.planning.domain.PlanningError.ProposalDeclined;
import com.dispatchflow.planning.domain.PlanningError.ProposalForAnotherPlan;
import com.dispatchflow.planning.domain.PlanningError.ProposalOutdated;
import com.dispatchflow.planning.domain.PlanningError.PublishingRefused;
import com.dispatchflow.planning.domain.PlanningError.RecheckFailed;
import com.dispatchflow.planning.domain.PlanningError.RecheckUnavailable;
import com.dispatchflow.planning.domain.PlanningError.RouteAlreadyStarted;
import com.dispatchflow.planning.domain.PlanningError.RouteIndexesUsedUp;
import com.dispatchflow.planning.domain.PlanningError.RouteLocked;
import com.dispatchflow.planning.domain.PlanningError.RouteNotInPlan;
import com.dispatchflow.planning.domain.PlanningError.RouteNotLocked;
import com.dispatchflow.planning.domain.PlanningError.RouteNotStaffed;
import com.dispatchflow.planning.domain.PlanningError.RouteWithdrawn;
import com.dispatchflow.planning.domain.PlanningError.StaleEvaluation;
import com.dispatchflow.planning.domain.PlanningError.StopAlreadyReported;
import com.dispatchflow.planning.domain.PlanningError.StopNotInPlan;
import com.dispatchflow.planning.domain.PlanningError.ValidatorUnavailable;
import com.dispatchflow.planning.domain.PlanningError.VehicleAlreadyHasRoute;
import com.dispatchflow.planning.domain.PlanningError.VehicleUnavailable;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.PlanCancelled;
import com.dispatchflow.planning.events.PlanPublished;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.planning.events.RevisionCause;
import com.dispatchflow.planning.events.RouteHandedOver;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * The intent for one delivery date: its routes and dropped orders, solved, edited, published and
 * changed by named revisions (DL §4.4, §9.1, BR-12). It is one aggregate on purpose: each order is
 * served or dropped once (BR-07), each vehicle and driver has at most one route (BR-39), a move is
 * atomic, and a revision is a consistent snapshot of every route (BR-110). Facts from the street
 * live in Live Operations; a late one corrects the plan through {@link #reconcileLateOutcome}
 * (DL §8, ADR-065).
 *
 * <p>Every command checks every rule before it changes anything, so a refused call leaves the state
 * and the pending events as they were. Every command also passes the state it would leave through
 * the same at-rest invariants {@link #restore} checks, so no command can leave a state that would
 * not load again. The rules no document settled are ADR-101's.
 */
public final class Plan extends AggregateRoot {

    /** Where a plan is in its lifecycle (DL §3.4, §9.1, BR-12). */
    public sealed interface Status {

        /** Opened, never solved, or its first solve failed. */
        record Draft() implements Status {}

        /**
         * A solve is queued or running; only its job may end it (BR-59).
         *
         * @param pending the solve the plan waits for
         */
        record Solving(SolveBasis pending) implements Status {

            /** Checks the basis is present. */
            public Solving {
                Objects.requireNonNull(pending, "pending");
            }
        }

        /** Solved, and open to edits and publishing (BR-60, BR-61). */
        record Ready() implements Status {}

        /**
         * The drivers' plan, changed only by new revisions (BR-47, BR-77).
         *
         * @param revision its current revision
         */
        record Published(RevisionNo revision) implements Status {

            /** Checks the revision is present. */
            public Published {
                Objects.requireNonNull(revision, "revision");
            }
        }

        /**
         * Its date is over; only late outcomes still change it, as new revisions (BR-102, ADR-101).
         *
         * @param revision its current revision
         */
        record Completed(RevisionNo revision) implements Status {

            /** Checks the revision is present. */
            public Completed {
                Objects.requireNonNull(revision, "revision");
            }
        }

        /**
         * Cancelled for a day that doesn't run; final, with its routes as they were (BR-103, ADR-101).
         *
         * @param revision its last revision; cancelling makes none
         * @param at when it was cancelled, which judging a late fact needs (BR-130)
         */
        record Cancelled(RevisionNo revision, Instant at) implements Status {

            /** Checks both parts are present. */
            public Cancelled {
                Objects.requireNonNull(revision, "revision");
                Objects.requireNonNull(at, "at");
            }
        }

        /** Never published by the midnight after its date; final and read-only (BR-127, ADR-053). */
        record Abandoned() implements Status {}
    }

    private final PlanId id;
    private final DeliveryDate date;
    private final Instant openedAt;
    private Status status;
    private @Nullable AppliedSolve applied;
    private Layout layout;
    private List<Violation> violations;
    private List<Edit> undo;
    private List<Edit> redo;
    private @Nullable PlanRevision pendingRevision;

    private Plan(PlanSnapshot state) {
        super(state.version());
        this.id = state.id();
        this.date = state.date();
        this.openedAt = state.openedAt();
        this.status = state.status();
        this.applied = state.applied().orElse(null);
        this.layout = new Layout(state.routes().stream().map(Route::from).toList(), state.dropped(), state.pins());
        this.violations = state.violations();
        this.undo = state.undo();
        this.redo = state.redo();
    }

    // ---- Creation and restore ------------------------------------------------------------------

    /**
     * Opens a plan for a date, in draft (FR-15).
     *
     * @param id the new plan's ID
     * @param date its delivery date
     * @param plansOnDate the date's uncancelled plan, as the use case read it
     * @param today the current date in {@code Asia/Jerusalem}
     * @param now the time of opening
     * @return the plan, at {@code Version.NEW}
     * @throws RuleViolation with {@link DateInPast} for a past date (BR-118), or {@link
     *     PlanAlreadyExists} when the date has an uncancelled plan (BR-34)
     */
    public static Plan open(PlanId id, DeliveryDate date, PlansOnDate plansOnDate, LocalDate today, Instant now) {
        if (date.isBefore(today)) {
            throw new RuleViolation(new DateInPast(date));
        }
        Optional<PlanId> existing = plansOnDate.uncancelled();
        if (existing.isPresent()) {
            throw new RuleViolation(new PlanAlreadyExists(date, existing.get()));
        }
        return new Plan(new PlanSnapshot(
                id,
                Version.NEW,
                date,
                now,
                new Status.Draft(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    }

    /**
     * Rebuilds a stored plan; only the persistence adapter calls it. It checks the invariants that
     * hold at rest, never one that needs {@code today} or an outside fact.
     *
     * @param snapshot the stored state
     * @return the plan
     * @throws StoredStateInvalid when the state breaks an invariant: a state's fields don't match
     *     it, an order and attempt appears twice (BR-07), a vehicle or driver drives two routes
     *     (BR-39), an ID or route index repeats, an order has two pins, or a violation or journal
     *     entry names a route or stop the plan doesn't hold as it should
     */
    public static Plan restore(PlanSnapshot snapshot) {
        Optional<String> broken = PlanInvariants.brokenBy(snapshot);
        if (broken.isPresent()) {
            throw new StoredStateInvalid(Plan.class, broken.get());
        }
        return new Plan(snapshot);
    }

    // ---- Solving -------------------------------------------------------------------------------

    /**
     * Requests a solve in the daily lane; the plan becomes solving, waiting for the new job (FR-15,
     * BR-60, DL §6.2). Its routes, drops and journal stay, so a failed solve returns to them (BR-59).
     *
     * @param jobId the new job's ID
     * @param solver the solver (FR-20)
     * @param limit the search limit (FR-19)
     * @param seed the seed (NFR-13)
     * @param capacity the daily lane's queue, read under its lock
     * @param today the current date in {@code Asia/Jerusalem}
     * @param now the time of the request
     * @return the new job, queued, to be created in the same transaction (DL §6.1)
     * @throws RuleViolation with {@link PlanNotDraftOrReady}, {@link DateInPast} (BR-118), or what
     *     {@code SolveJob.queue} refuses with
     */
    public SolveJob requestSolve(
            JobId jobId,
            SolverName solver,
            SolveLimit limit,
            Seed seed,
            LaneCapacity capacity,
            LocalDate today,
            Instant now) {
        if (!(status instanceof Status.Draft || status instanceof Status.Ready)) {
            throw new RuleViolation(new PlanNotDraftOrReady(id));
        }
        requireNotPast(today);
        SolveJob job = SolveJob.queue(jobId, id, Lane.DAILY, solver, limit, seed, null, capacity, now);
        commit(next().status(new Status.Solving(new SolveBasis(jobId, now))));
        return job;
    }

    /**
     * Requests a re-optimization of the published plan's remaining stops (FR-35). The plan doesn't
     * change and isn't saved; only the new job is (DL §6.2, ADR-101).
     *
     * @param jobId the new job's ID
     * @param solver the solver (FR-20)
     * @param seed the seed (NFR-13)
     * @param capacity the re-optimization lane's queue, read under its lock
     * @param today the current date in {@code Asia/Jerusalem}
     * @param now the time of the request
     * @return the new job, queued with a 20-second limit (BR-84) and the plan's revision as its basis
     * @throws RuleViolation with {@link PlanNotPublished}, {@link DateInPast} (BR-118), or what {@code
     *     SolveJob.queue} refuses with
     */
    public SolveJob requestReoptimization(
            JobId jobId, SolverName solver, Seed seed, LaneCapacity capacity, LocalDate today, Instant now) {
        Status.Published published = requirePublished();
        requireNotPast(today);
        return SolveJob.queue(
                jobId,
                id,
                Lane.REOPTIMIZATION,
                solver,
                SolveLimit.REOPTIMIZATION,
                seed,
                published.revision(),
                capacity,
                now);
    }

    /**
     * Applies the result of the job the plan is solving for: its routes and drops replace the plan's,
     * and the plan becomes ready (BR-15, BR-59). A result from any other job, or in any other state,
     * changes nothing, so the listener's repeats are harmless; that includes an abandoned plan
     * (ADR-101).
     *
     * <p>A vehicle keeps its route's ID and index, an order and attempt keeps its stop's ID, and a
     * new vehicle takes the lowest free index (ADR-101). A locked route must come back as locked
     * (FR-147); the journal is cleared (FR-145).
     *
     * @param job the ended job, whose result the plan applies
     * @param ids new IDs for new routes and stops
     * @param now when the plan applies it
     * @throws RuleViolation with {@link RouteLocked} when the result changes a locked route, {@link
     *     OrderAlreadyInPlan} (BR-07), or {@link VehicleAlreadyHasRoute} or {@link
     *     DriverAlreadyHasRoute} (BR-39); each means the optimizer broke its contract
     */
    public void applySolveResult(SolveJob job, FreshIds ids, Instant now) {
        if (!(status instanceof Status.Solving solving)
                || !solving.pending().job().equals(job.id())
                || !job.plan().equals(id)
                || job.result().isEmpty()) {
            return;
        }
        SolveResult result = job.result().get();
        for (Route locked : layout.routes()) {
            if (locked.lock().isPresent() && !keptBy(locked, result.routes())) {
                throw new RuleViolation(new RouteLocked(locked.id()));
            }
        }
        requireSingleAssignment(result.routes(), result.dropped());
        List<Route> placed =
                place(result.routes(), layout.routes(), layout.routes(), List.of(), ids, vehicle -> Optional.empty());
        commit(next().status(new Status.Ready())
                .applied(new AppliedSolve(solving.pending(), now))
                .layout(new Layout(carryLocks(placed), result.dropped(), layout.pins()))
                .violations(List.of())
                .clearingJournal());
    }

    /**
     * The job the plan is solving for failed: the plan returns to ready if it was solved before, and
     * otherwise to draft, with its routes as they were (BR-57, BR-59). Any other job, or any other
     * state, changes nothing; that includes an abandoned plan (ADR-053).
     *
     * @param jobId the job that failed
     */
    public void solveFailed(JobId jobId) {
        if (status instanceof Status.Solving solving && solving.pending().job().equals(jobId)) {
            commit(next().status(applied == null ? new Status.Draft() : new Status.Ready()));
        }
    }

    // ---- Manual edits (ready only, except pins) ------------------------------------------------

    /**
     * Moves a stop to a position in its route or another (FR-22). The stop keeps its ID.
     *
     * @param stop the stop
     * @param toRoute the route it goes to
     * @param position its position there, from 0, counted in the route without it
     * @param evaluation the validator's judgement of the plan after the move
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotReady}, {@link DateInPast}, {@link StaleEvaluation},
     *     {@link ValidatorUnavailable}, {@link StopNotInPlan}, {@link RouteNotInPlan}, {@link
     *     PositionOutOfRange}, or {@link RouteLocked} for either route (FR-147)
     */
    public void moveStop(StopId stop, RouteId toRoute, int position, Evaluation evaluation, LocalDate today) {
        requireReady();
        requireNotPast(today);
        requireCurrentAndAvailable(evaluation);
        Located from = locate(stop);
        Route target = route(toRoute);
        int last = from.route().id().equals(toRoute)
                ? target.stops().size() - 1
                : target.stops().size();
        requirePosition(toRoute, position, last);
        requireUnlocked(from.route());
        requireUnlocked(target);
        commitEdit(
                layout.moving(from.stop(), toRoute, position),
                evaluation,
                new Edit.MovedStop(stop, from.route().id(), from.position(), toRoute, position));
    }

    /**
     * Takes a stop off its route into the drops, as removed by the dispatcher (FR-146, FR-236).
     *
     * @param stop the stop
     * @param evaluation the validator's judgement of the plan after the removal
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotReady}, {@link DateInPast}, {@link StaleEvaluation},
     *     {@link ValidatorUnavailable}, {@link StopNotInPlan}, or {@link RouteLocked}
     */
    public void takeOffRoute(StopId stop, Evaluation evaluation, LocalDate today) {
        requireReady();
        requireNotPast(today);
        requireCurrentAndAvailable(evaluation);
        Located at = locate(stop);
        requireUnlocked(at.route());
        PlannedStop taken = at.stop();
        commitEdit(
                takingOff(layout, taken),
                evaluation,
                new Edit.TookOff(
                        stop, taken.order(), taken.attempt(), at.route().id(), at.position(), taken.plan()));
    }

    /**
     * Puts a dropped order on a route by hand, as a new stop (FR-235).
     *
     * @param order the order
     * @param attempt its attempt
     * @param toRoute the route
     * @param position its position there, from 0
     * @param newStop the new stop's ID
     * @param evaluation the validator's judgement of the plan after the placement
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotReady}, {@link DateInPast}, {@link StaleEvaluation},
     *     {@link ValidatorUnavailable}, {@link OrderNotDropped}, {@link OrderNotRoutable} for a missing
     *     or unreachable location (FR-237), {@link RouteNotInPlan}, {@link PositionOutOfRange}, or
     *     {@link RouteLocked}
     */
    public void placeDropped(
            OrderId order,
            Attempt attempt,
            RouteId toRoute,
            int position,
            StopId newStop,
            Evaluation evaluation,
            LocalDate today) {
        requireReady();
        requireNotPast(today);
        requireCurrentAndAvailable(evaluation);
        StopKey key = new StopKey(order, attempt);
        DroppedOrder drop = layout.drop(key).orElseThrow(() -> new RuleViolation(new OrderNotDropped(order, attempt)));
        if (drop.reason().isAboutLocation()) {
            throw new RuleViolation(new OrderNotRoutable(order));
        }
        Route target = route(toRoute);
        requirePosition(toRoute, position, target.stops().size());
        requireUnlocked(target);
        Layout after = layout.without(key)
                .inserting(
                        toRoute,
                        position,
                        new PlannedStop(newStop, order, attempt, Optional.empty(), Optional.empty()));
        commitEdit(after, evaluation, new Edit.Placed(order, attempt, newStop, toRoute, position, drop.reason()));
    }

    /**
     * Pins an order to a vehicle or a driver, in draft or ready, replacing any pin it had (FR-91). In
     * draft the evaluation covers a plan with no routes and checks the version, and a draft keeps no
     * violations, so the evaluation's are not stored; only a ready plan's pin is journaled (FR-145,
     * ADR-101).
     *
     * @param order the order
     * @param target the vehicle or driver
     * @param evaluation the validator's judgement of the plan with the pin
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotDraftOrReady}, {@link DateInPast}, {@link
     *     StaleEvaluation}, or {@link ValidatorUnavailable}
     */
    public void pin(OrderId order, PinTarget target, Evaluation evaluation, LocalDate today) {
        requireDraftOrReady();
        requireNotPast(today);
        requireCurrentAndAvailable(evaluation);
        Optional<PinTarget> before = layout.pinOf(order);
        commitPinEdit(layout.pinning(order, Optional.of(target)), evaluation, new Edit.Pinned(order, before, target));
    }

    /**
     * Removes an order's pin, in draft or ready (FR-91).
     *
     * @param order the order
     * @param evaluation the validator's judgement of the plan without the pin
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotDraftOrReady}, {@link DateInPast}, {@link
     *     StaleEvaluation}, {@link ValidatorUnavailable}, or {@link OrderNotPinned}
     */
    public void unpin(OrderId order, Evaluation evaluation, LocalDate today) {
        requireDraftOrReady();
        requireNotPast(today);
        requireCurrentAndAvailable(evaluation);
        PinTarget before = layout.pinOf(order).orElseThrow(() -> new RuleViolation(new OrderNotPinned(order)));
        commitPinEdit(layout.pinning(order, Optional.empty()), evaluation, new Edit.Unpinned(order, before));
    }

    /**
     * Locks a route against edits and solves, replacing any lock it had (FR-147). A lock changes no
     * route, so it needs no evaluation.
     *
     * @param route the route
     * @param lock the kind of lock
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotReady}, {@link DateInPast}, or {@link RouteNotInPlan}
     */
    public void lockRoute(RouteId route, RouteLock lock, LocalDate today) {
        requireReady();
        requireNotPast(today);
        Route locked = route(route);
        commit(next().layout(layout.locking(route, Optional.of(lock)))
                .journaling(new Edit.Locked(route, locked.lock(), lock)));
    }

    /**
     * Removes a route's lock (FR-147).
     *
     * @param route the route
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotReady}, {@link DateInPast}, {@link RouteNotInPlan}, or
     *     {@link RouteNotLocked}
     */
    public void unlockRoute(RouteId route, LocalDate today) {
        requireReady();
        requireNotPast(today);
        RouteLock before = route(route).lock().orElseThrow(() -> new RuleViolation(new RouteNotLocked(route)));
        commit(next().layout(layout.locking(route, Optional.empty())).journaling(new Edit.Unlocked(route, before)));
    }

    /**
     * Undoes the last edit since the last solve (FR-145).
     *
     * @param evaluation the validator's judgement of the plan after the undo
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotReady}, {@link DateInPast}, {@link NothingToUndo},
     *     {@link StaleEvaluation}, or {@link ValidatorUnavailable}
     */
    public void undo(Evaluation evaluation, LocalDate today) {
        requireReady();
        requireNotPast(today);
        if (undo.isEmpty()) {
            throw new RuleViolation(new NothingToUndo());
        }
        requireCurrentAndAvailable(evaluation);
        Edit last = undo.get(undo.size() - 1);
        Layout judged = reverted(last).judgedBy(evaluation, version(), true);
        commit(next().layout(judged)
                .violations(evaluation.violations())
                .undo(undo.subList(0, undo.size() - 1))
                .redo(appended(redo, last)));
    }

    /**
     * Redoes the last undone edit (FR-145).
     *
     * @param evaluation the validator's judgement of the plan after the redo
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotReady}, {@link DateInPast}, {@link NothingToRedo},
     *     {@link StaleEvaluation}, or {@link ValidatorUnavailable}
     */
    public void redo(Evaluation evaluation, LocalDate today) {
        requireReady();
        requireNotPast(today);
        if (redo.isEmpty()) {
            throw new RuleViolation(new NothingToRedo());
        }
        requireCurrentAndAvailable(evaluation);
        Edit last = redo.get(redo.size() - 1);
        Layout judged = reapplied(last).judgedBy(evaluation, version(), true);
        commit(next().layout(judged)
                .violations(evaluation.violations())
                .redo(redo.subList(0, redo.size() - 1))
                .undo(appended(undo, last)));
    }

    // ---- Publishing and the published plan -----------------------------------------------------

    /**
     * Publishes the plan as revision 1, with the re-checked times (FR-26, BR-68). The four checks of
     * BR-72 run in order, and the first that refuses lists every refusal it found. Raises {@link
     * PlanPublished}.
     *
     * @param checks the roster's answers, the date's orders and the optimizer's re-check
     * @param by who publishes
     * @param today the current date in {@code Asia/Jerusalem}
     * @param now the time of publishing
     * @throws RuleViolation with {@link PlanNotReady}, {@link DateInPast} (BR-118), or {@link
     *     StaleEvaluation} for a re-check of another version or layout
     * @throws RuleViolations with {@link PublishingRefused} and that step's refusals: {@link
     *     RouteNotStaffed}; every {@link OrderNotAccounted}, then every {@link OrderChangedSinceSolve};
     *     {@link OpenViolation}; {@link RecheckUnavailable} or {@link RecheckFailed}
     */
    public void publish(PublishingChecks checks, AccountId by, LocalDate today, Instant now) {
        requireReady();
        requireNotPast(today);
        Evaluation recheck = checks.recheck();
        requireCurrent(recheck);
        RuleViolations.throwIfAny(new PublishingRefused(PublishingStep.STAFFING), unstaffed(checks.staffing()));
        RuleViolations.throwIfAny(new PublishingRefused(PublishingStep.ORDERS), unaccounted(checks.orders()));
        RuleViolations.throwIfAny(
                new PublishingRefused(PublishingStep.VIOLATIONS),
                violations.stream()
                        .map(violation -> new OpenViolation(violation.rule(), violation.route(), violation.order()))
                        .toList());
        if (!layout.routes().isEmpty()) {
            List<PlanningError> refusals = recheck.available()
                    ? recheck.violations().stream()
                            .<PlanningError>map(violation ->
                                    new RecheckFailed(violation.rule(), violation.route(), violation.order()))
                            .toList()
                    : List.of(new RecheckUnavailable());
            RuleViolations.throwIfAny(new PublishingRefused(PublishingStep.RECHECK), refusals);
        }
        Layout published = recheck.available() ? layout.judgedBy(recheck, version(), true) : layout;
        commit(next().layout(published).clearingJournal().status(new Status.Published(RevisionNo.FIRST)));
        pendingRevision = revisionOf(RevisionNo.FIRST, RevisionCause.PUBLISHED, Optional.empty(), Optional.of(by), now);
        List<RouteId> routes = routeIds(published.routes());
        raise(PlanPublished.class, id.value(), eventId -> new PlanPublished(eventId, now, id, date, 1, routes));
    }

    /**
     * Accepts a re-optimization: its routes and drops replace the plan's, as a new revision that cites
     * it (FR-36, BR-62, ADR-056). It takes the proposal itself, which only a succeeded job or
     * persistence builds, so only a validated solution reaches the plan (BR-15). Raises {@link
     * PlanRevised}.
     *
     * <p>A vehicle keeps its route's ID and index, and its confirmed return when it keeps its driver;
     * an order and attempt keeps its stop's ID, from any route, withdrawn ones included. A withdrawn
     * vehicle's route is marked with its cause (FR-271), and keeps the stops the proposal doesn't
     * serve or drop when the proposal gives it no route. A vehicle withdrawn earlier that the proposal
     * withdraws again keeps that route's ID and index, and takes the proposal's cause and stops. Any
     * other route withdrawn earlier stays as it is (FR-268), and an order it holds that the proposal
     * serves or drops elsewhere refuses the acceptance (BR-07, ADR-101).
     *
     * @param proposal the proposal
     * @param changes what was recorded since its basis
     * @param ids new IDs for new routes and stops
     * @param by who accepts it
     * @param today the current date in {@code Asia/Jerusalem}
     * @param now the time of acceptance
     * @throws RuleViolation with {@link PlanNotPublished}, {@link DateInPast}, {@link
     *     ProposalForAnotherPlan}, {@link ProposalDeclined}, {@link ProposalOutdated} naming every
     *     change, including any revision since its basis (BR-25, BR-82), {@link VehicleUnavailable}
     *     naming every vehicle (BR-64, BR-65), {@link OrderAlreadyInPlan}, {@link
     *     VehicleAlreadyHasRoute}, {@link DriverAlreadyHasRoute}, or {@link RouteIndexesUsedUp}
     */
    public void accept(
            ReoptimizationProposal proposal,
            ChangesSinceBasis changes,
            FreshIds ids,
            AccountId by,
            LocalDate today,
            Instant now) {
        Status.Published published = requirePublished();
        requireNotPast(today);
        ReoptimizationProposalSnapshot offer = proposal.snapshot();
        if (!offer.plan().equals(id)) {
            throw new RuleViolation(new ProposalForAnotherPlan(offer.id(), offer.plan()));
        }
        if (offer.state() instanceof ReoptimizationProposal.State.Declined) {
            throw new RuleViolation(new ProposalDeclined(offer.id()));
        }
        List<BasisChange> outdated = changesSince(offer, changes, published.revision());
        if (!outdated.isEmpty()) {
            throw new RuleViolation(new ProposalOutdated(offer.id(), outdated));
        }
        List<VehicleId> unavailable = unavailableVehicles(offer, changes);
        if (!unavailable.isEmpty()) {
            throw new RuleViolation(new VehicleUnavailable(unavailable));
        }
        requireSingleAssignment(offer.routes(), offer.dropped());
        List<Route> before = layout.routes();
        List<Route> active = before.stream().filter(route -> !route.withdrawn()).toList();
        List<Route> uncovered = uncoveredWithdrawals(offer, active);
        List<Route> covered = active.stream()
                .filter(route -> uncovered.stream().noneMatch(kept -> kept.id().equals(route.id())))
                .toList();
        List<Route> rewithdrawn = rewithdrawnRoutes(offer, before, covered);
        List<Route> earlierWithdrawn = before.stream()
                .filter(route -> route.withdrawn() && !rewithdrawn.contains(route))
                .toList();
        List<Route> matchable = new ArrayList<>(covered);
        matchable.addAll(rewithdrawn);
        List<Route> kept = new ArrayList<>(earlierWithdrawn);
        kept.addAll(uncovered);
        List<Route> placed =
                place(offer.routes(), matchable, before, kept, ids, vehicle -> withdrawalFor(offer, vehicle));
        List<Route> next = new ArrayList<>(earlierWithdrawn);
        next.addAll(placed);
        next.addAll(uncovered);
        Layout after = new Layout(next, offer.dropped(), layout.pins());
        requireEachOrderOnce(after);
        List<VehicleId> newlyWithdrawn = next.stream()
                .filter(route -> route.withdrawn()
                        && before.stream().noneMatch(old -> old.id().equals(route.id()) && old.withdrawn()))
                .map(Route::vehicle)
                .toList();
        List<RouteId> changed = changedRoutes(before, next);
        revise(
                next().layout(after).violations(List.of()),
                RevisionCause.REOPTIMIZATION_ACCEPTED,
                Optional.of(offer.id()),
                Optional.of(by),
                now,
                changed,
                newlyWithdrawn);
    }

    /**
     * Hands a route of the published plan to another driver; its stops and times stay, and its
     * reported stops keep their deliveries (FR-55, FR-192, FR-303). Raises {@link PlanRevised}, then
     * {@link RouteHandedOver}, so the roster follows (FR-191).
     *
     * <p>With an available evaluation, which must have judged the route with the new driver, the
     * route is checked; a broken shift (BR-06) needs the dispatcher's confirmation, and a confirmed
     * one sets the route's confirmed return to the evaluated return (FR-304, FR-394). With none, or an
     * unavailable one, the route is not yet checked (FR-395, ADR-058).
     *
     * @param route the route
     * @param standing the new driver's standing
     * @param evaluation the validator's judgement of the plan after the hand-over, if it could be read
     * @param breakConfirmed whether the dispatcher confirmed a broken shift
     * @param by who hands it over
     * @param today the current date in {@code Asia/Jerusalem}
     * @param now the time of the hand-over
     * @throws RuleViolation with {@link PlanNotPublished}, {@link DateInPast}, {@link RouteNotInPlan},
     *     {@link RouteWithdrawn}, {@link StaleEvaluation}, {@link NotAnActiveDriver}, {@link
     *     DriverNotAvailable}, {@link DriverAlreadyHasRoute} (FR-302), or {@link BreakNotConfirmed}
     */
    public void handOver(
            RouteId route,
            DriverStanding standing,
            Optional<Evaluation> evaluation,
            boolean breakConfirmed,
            AccountId by,
            LocalDate today,
            Instant now) {
        requirePublished();
        requireNotPast(today);
        Route current = route(route);
        if (current.withdrawn()) {
            throw new RuleViolation(new RouteWithdrawn(route));
        }
        evaluation.ifPresent(this::requireCurrent);
        AccountId driver = standing.driver();
        if (!standing.activeDriver()) {
            throw new RuleViolation(new NotAnActiveDriver(driver));
        }
        if (!standing.available()) {
            throw new RuleViolation(new DriverNotAvailable(driver));
        }
        Optional<Route> driving = layout.routes().stream()
                .filter(candidate ->
                        !candidate.withdrawn() && candidate.driver().equals(driver))
                .findFirst();
        if (driving.isPresent()) {
            throw new RuleViolation(
                    new DriverAlreadyHasRoute(driver, driving.get().id()));
        }
        Layout after = layout.changing(route, handed -> handed.withDriver(driver));
        List<Violation> nextViolations = violations;
        Optional<Evaluation> judging = evaluation.filter(Evaluation::available);
        if (judging.isPresent()) {
            Evaluation judged = judging.get();
            after = after.judgedBy(judged, version(), false);
            boolean breaks = judged.breaksShift(route);
            if (breaks && !breakConfirmed) {
                throw new RuleViolation(new BreakNotConfirmed(route, driver));
            }
            Optional<DaySeconds> confirmed = breaks ? judged.returnOf(route) : Optional.empty();
            after = after.changing(route, handed -> handed.withConfirmedReturn(confirmed));
            nextViolations = judged.violations().stream()
                    .filter(violation -> !(breaks && violation.breaks(RuleCode.SHIFTS, route)))
                    .toList();
        } else {
            after = after.changing(
                    route,
                    handed -> handed.withCheck(CheckState.NOT_YET_CHECKED).withConfirmedReturn(Optional.empty()));
        }
        revise(
                next().layout(after).violations(nextViolations),
                RevisionCause.HANDED_OVER,
                Optional.empty(),
                Optional.of(by),
                now,
                List.of(route),
                List.of());
        AccountId from = current.driver();
        raise(
                RouteHandedOver.class,
                id.value(),
                eventId -> new RouteHandedOver(eventId, now, id, date, route, current.vehicle(), from, driver));
    }

    /**
     * Takes an order's stop off the plan at once, without optimizing, after the order was cancelled,
     * rescheduled or corrected after a put-back (FR-207, FR-315, FR-316, FR-404). It overrides a lock.
     * On a ready plan the journal is cleared (FR-145); on a published plan it is a revision with the
     * cause, and raises {@link PlanRevised}. The stop's route becomes not yet checked, for the re-check
     * job (FR-395). In any other state, or for an order the plan doesn't hold, nothing changes
     * (ADR-101).
     *
     * @param order the order
     * @param attempt its attempt
     * @param cause why it leaves
     * @param now the time of the change
     */
    public void removeStopOf(OrderId order, Attempt attempt, RemovalCause cause, Instant now) {
        boolean ready = status instanceof Status.Ready;
        if (!ready && !(status instanceof Status.Published)) {
            return;
        }
        StopKey key = new StopKey(order, attempt);
        Optional<Located> located = layout.locate(key);
        Layout after;
        List<RouteId> changed;
        if (located.isPresent()) {
            RouteId route = located.get().route().id();
            after = layout.removing(located.get().stop().id())
                    .changing(route, unchecked -> unchecked.withCheck(CheckState.NOT_YET_CHECKED));
            changed = List.of(route);
        } else if (layout.drop(key).isPresent()) {
            after = layout.without(key);
            changed = List.of();
        } else {
            return;
        }
        if (ready) {
            commit(next().layout(after).clearingJournal());
        } else {
            revise(
                    next().layout(after),
                    cause.revisionCause(),
                    Optional.empty(),
                    Optional.empty(),
                    now,
                    changed,
                    List.of());
        }
    }

    /**
     * Puts a late fact's stop on the route that served it (DL §8, BR-89, BR-130). The stop is found
     * by its ID or by its order and attempt, so an order is never added twice. A completed plan
     * accepts it too, as a new revision, and is not republished; a cancelled plan stays as it is
     * (ADR-101). A change raises {@link PlanRevised}; a repeat changes nothing.
     *
     * @param fact the accepted outcome and the route that served it
     * @param now the time of the reconciliation
     * @return what changed, for the dispatcher's alert
     * @throws RuleViolation with {@link PlanNotPublished} for a plan never published, or {@link
     *     RouteNotInPlan} for an unknown serving route
     */
    public Reconciliation reconcileLateOutcome(LateFact fact, Instant now) {
        if (status instanceof Status.Cancelled) {
            return Reconciliation.of(ReconciliationKind.PLAN_CANCELLED);
        }
        if (!(status instanceof Status.Published || status instanceof Status.Completed)) {
            throw new RuleViolation(new PlanNotPublished(id));
        }
        RouteId servedOn = fact.servedOn();
        route(servedOn);
        StopKey key = new StopKey(fact.order(), fact.attempt());
        Optional<Located> located = layout.locate(fact.stop()).or(() -> layout.locate(key));
        Layout after;
        Reconciliation reconciliation;
        Set<RouteId> changed = new HashSet<>(List.of(servedOn));
        if (located.isPresent()) {
            Located at = located.get();
            if (at.route().id().equals(servedOn)) {
                return Reconciliation.of(ReconciliationKind.UNCHANGED);
            }
            PlannedStop stop = at.stop();
            PlannedStop moved = new PlannedStop(
                    stop.id(), stop.order(), stop.attempt(), stop.plan(), Optional.of(LateOutcome.SERVED_LATE));
            after = layout.removing(stop.id()).appending(servedOn, moved);
            changed.add(at.route().id());
            reconciliation = Reconciliation.movedFrom(at.route().id());
        } else if (layout.drop(key).isPresent()) {
            after = layout.without(key).appending(servedOn, lateStop(fact, LateOutcome.SERVED_LATE));
            reconciliation = Reconciliation.of(ReconciliationKind.FROM_DROPPED);
        } else {
            after = layout.appending(servedOn, lateStop(fact, LateOutcome.SERVED_AFTER_CANCELLATION));
            reconciliation = Reconciliation.of(ReconciliationKind.AFTER_CANCELLATION);
        }
        List<RouteId> changedInOrder =
                after.routes().stream().map(Route::id).filter(changed::contains).toList();
        revise(
                next().layout(after),
                RevisionCause.LATE_OUTCOME,
                Optional.empty(),
                Optional.empty(),
                now,
                changedInOrder,
                List.of());
        return reconciliation;
    }

    /**
     * Records the validator's check of routes changed without one, from the re-check job (FR-395,
     * ADR-058). It marks every route checked and replaces the open violations; it changes no times or
     * stops, so it makes no revision (FR-207). An unavailable evaluation, or a plan neither ready nor
     * published, changes nothing (ADR-101).
     *
     * @param evaluation the validator's judgement of the plan as it is
     * @throws RuleViolation with {@link StaleEvaluation} for another version or layout
     */
    public void recordRouteCheck(Evaluation evaluation) {
        if (!(status instanceof Status.Ready || status instanceof Status.Published)) {
            return;
        }
        requireCurrent(evaluation);
        if (!evaluation.available()) {
            return;
        }
        commit(next().layout(layout.judgedBy(evaluation, version(), false)).violations(evaluation.violations()));
    }

    /**
     * Cancels the published plan for a day that doesn't run, while no route has started and no stop
     * is reported (FR-336, BR-103). Its routes stay as they were, and it overrides any lock; cancelling
     * makes no revision. Raises {@link PlanCancelled}, so its drivers are alerted (FR-337).
     *
     * @param started what has happened on its routes
     * @param today the current date in {@code Asia/Jerusalem}
     * @param now the time of cancelling
     * @throws RuleViolation with {@link PlanNotPublished}, {@link DateInPast}, {@link
     *     RouteAlreadyStarted} naming every started route, or {@link StopAlreadyReported} naming every
     *     reported stop
     */
    public void cancel(StartedRoutes started, LocalDate today, Instant now) {
        Status.Published published = requirePublished();
        requireNotPast(today);
        if (!started.started().isEmpty()) {
            throw new RuleViolation(new RouteAlreadyStarted(started.started()));
        }
        if (!started.reported().isEmpty()) {
            throw new RuleViolation(new StopAlreadyReported(started.reported()));
        }
        commit(next().status(new Status.Cancelled(published.revision(), now)));
        List<RouteId> routes = routeIds(layout.routes());
        raise(PlanCancelled.class, id.value(), eventId -> new PlanCancelled(eventId, now, id, date, routes));
    }

    /**
     * Completes the published plan at the midnight after its date (BR-102).
     *
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotPublished}, or {@link DateNotOver} before that midnight
     */
    public void complete(LocalDate today) {
        Status.Published published = requirePublished();
        requireDateOver(today);
        commit(next().status(new Status.Completed(published.revision())));
    }

    /**
     * Abandons a plan still unpublished at the midnight after its date; it is then final and
     * read-only (BR-127, ADR-053). A solve it waited for is forgotten: the midnight job failed it first
     * (BR-128).
     *
     * @param today the current date in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link PlanNotDraftSolvingOrReady}, or {@link DateNotOver} before that
     *     midnight
     */
    public void abandon(LocalDate today) {
        if (!(status instanceof Status.Draft || status instanceof Status.Solving || status instanceof Status.Ready)) {
            throw new RuleViolation(new PlanNotDraftSolvingOrReady(id));
        }
        requireDateOver(today);
        commit(next().clearingJournal().status(new Status.Abandoned()));
    }

    // ---- Queries -------------------------------------------------------------------------------

    /**
     * The plan's ID.
     *
     * @return the ID
     */
    public PlanId id() {
        return id;
    }

    /**
     * The plan's state.
     *
     * @return the state
     */
    public Status status() {
        return status;
    }

    /**
     * The revision this change made, which the repository appends when it saves the plan (ADR-059).
     *
     * @return the revision, or empty when this change made none
     */
    public Optional<PlanRevision> pendingRevision() {
        return Optional.ofNullable(pendingRevision);
    }

    /**
     * The plan's present state, for the repository, for Planning's queries, and for tests.
     *
     * @return a snapshot that {@link #restore} rebuilds
     */
    public PlanSnapshot snapshot() {
        return snapshotOf(status, applied, layout, violations, undo, redo);
    }

    // ---- The one commit point ------------------------------------------------------------------

    /** The state a command would leave; it starts as the plan's present state. */
    private final class Next {

        private Status status = Plan.this.status;
        private @Nullable AppliedSolve applied = Plan.this.applied;
        private Layout layout = Plan.this.layout;
        private List<Violation> violations = Plan.this.violations;
        private List<Edit> undo = Plan.this.undo;
        private List<Edit> redo = Plan.this.redo;

        Next status(Status next) {
            this.status = next;
            return this;
        }

        Next applied(AppliedSolve next) {
            this.applied = next;
            return this;
        }

        Next layout(Layout next) {
            this.layout = next;
            return this;
        }

        Next violations(List<Violation> next) {
            this.violations = List.copyOf(next);
            return this;
        }

        Next undo(List<Edit> next) {
            this.undo = List.copyOf(next);
            return this;
        }

        Next redo(List<Edit> next) {
            this.redo = List.copyOf(next);
            return this;
        }

        Next journaling(Edit edit) {
            return undo(appended(undo, edit)).redo(List.of());
        }

        Next clearingJournal() {
            return undo(List.of()).redo(List.of());
        }
    }

    private Next next() {
        return new Next();
    }

    /**
     * Replaces the plan's state with the one a command built, after checking it holds every at-rest
     * invariant. Every rule a caller can break was checked before this point, so a broken invariant
     * here is a bug in the command, not a refusal.
     */
    private void commit(Next next) {
        PlanSnapshot candidate =
                snapshotOf(next.status, next.applied, next.layout, next.violations, next.undo, next.redo);
        Optional<String> broken = PlanInvariants.brokenBy(candidate);
        if (broken.isPresent()) {
            throw new AssertionError("a plan command would leave a state that breaks: " + broken.get());
        }
        status = next.status;
        applied = next.applied;
        layout = next.layout;
        violations = next.violations;
        undo = next.undo;
        redo = next.redo;
    }

    private PlanSnapshot snapshotOf(
            Status state,
            @Nullable AppliedSolve solve,
            Layout routes,
            List<Violation> open,
            List<Edit> undone,
            List<Edit> redone) {
        return new PlanSnapshot(
                id,
                version(),
                date,
                openedAt,
                state,
                Optional.ofNullable(solve),
                routes.routes().stream().map(Route::snapshot).toList(),
                routes.dropped(),
                routes.pins(),
                open,
                undone,
                redone);
    }

    // ---- Checks --------------------------------------------------------------------------------

    private void requireReady() {
        if (!(status instanceof Status.Ready)) {
            throw new RuleViolation(new PlanNotReady(id));
        }
    }

    private void requireDraftOrReady() {
        if (!(status instanceof Status.Draft || status instanceof Status.Ready)) {
            throw new RuleViolation(new PlanNotDraftOrReady(id));
        }
    }

    private Status.Published requirePublished() {
        if (!(status instanceof Status.Published published)) {
            throw new RuleViolation(new PlanNotPublished(id));
        }
        return published;
    }

    private void requireNotPast(LocalDate today) {
        if (date.isBefore(today)) {
            throw new RuleViolation(new DateInPast(date));
        }
    }

    private void requireDateOver(LocalDate today) {
        if (!date.isBefore(today)) {
            throw new RuleViolation(new DateNotOver(date));
        }
    }

    private void requireCurrent(Evaluation evaluation) {
        if (!evaluation.planVersion().equals(version())) {
            throw new RuleViolation(new StaleEvaluation(evaluation.planVersion(), version()));
        }
    }

    private void requireCurrentAndAvailable(Evaluation evaluation) {
        requireCurrent(evaluation);
        if (!evaluation.available()) {
            throw new RuleViolation(new ValidatorUnavailable());
        }
    }

    private Route route(RouteId route) {
        return layout.route(route).orElseThrow(() -> new RuleViolation(new RouteNotInPlan(route)));
    }

    private Located locate(StopId stop) {
        return layout.locate(stop).orElseThrow(() -> new RuleViolation(new StopNotInPlan(stop)));
    }

    private static void requirePosition(RouteId route, int position, int last) {
        if (position < 0 || position > last) {
            throw new RuleViolation(new PositionOutOfRange(route, position));
        }
    }

    private static void requireUnlocked(Route route) {
        if (route.lock().isPresent()) {
            throw new RuleViolation(new RouteLocked(route.id()));
        }
    }

    private static void requireSingleAssignment(List<SolvedRoute> routes, List<DroppedOrder> dropped) {
        Set<StopKey> seen = new HashSet<>();
        for (StopKey key : keysOf(routes, dropped)) {
            if (!seen.add(key)) {
                throw new RuleViolation(new OrderAlreadyInPlan(key.order(), key.attempt()));
            }
        }
    }

    private static void requireOneRoutePerVehicleAndDriver(List<Route> routes) {
        Map<VehicleId, RouteId> vehicles = new HashMap<>();
        Map<AccountId, RouteId> drivers = new HashMap<>();
        for (Route route : routes) {
            if (route.withdrawn()) {
                continue;
            }
            RouteId vehicleRoute = vehicles.putIfAbsent(route.vehicle(), route.id());
            if (vehicleRoute != null) {
                throw new RuleViolation(new VehicleAlreadyHasRoute(route.vehicle(), vehicleRoute));
            }
            RouteId driverRoute = drivers.putIfAbsent(route.driver(), route.id());
            if (driverRoute != null) {
                throw new RuleViolation(new DriverAlreadyHasRoute(route.driver(), driverRoute));
            }
        }
    }

    // ---- Edits ---------------------------------------------------------------------------------

    private void commitEdit(Layout after, Evaluation evaluation, Edit edit) {
        commit(next().layout(after.judgedBy(evaluation, version(), true))
                .violations(evaluation.violations())
                .journaling(edit));
    }

    private void commitPinEdit(Layout after, Evaluation evaluation, Edit edit) {
        Next change = next().layout(after.judgedBy(evaluation, version(), true));
        if (status instanceof Status.Ready) {
            change.violations(evaluation.violations()).journaling(edit);
        }
        commit(change);
    }

    private Layout reverted(Edit edit) {
        return switch (edit) {
            case Edit.MovedStop moved -> layout.moving(stopAt(moved.stop()), moved.fromRoute(), moved.fromPosition());
            case Edit.TookOff took ->
                layout.without(new StopKey(took.order(), took.attempt()))
                        .inserting(
                                took.route(),
                                took.position(),
                                new PlannedStop(
                                        took.stop(), took.order(), took.attempt(), took.plan(), Optional.empty()));
            case Edit.Placed placed ->
                layout.removing(placed.stop())
                        .adding(new DroppedOrder(placed.order(), placed.attempt(), placed.before()));
            case Edit.Pinned pinned -> layout.pinning(pinned.order(), pinned.before());
            case Edit.Unpinned unpinned -> layout.pinning(unpinned.order(), Optional.of(unpinned.before()));
            case Edit.Locked locked -> layout.locking(locked.route(), locked.before());
            case Edit.Unlocked unlocked -> layout.locking(unlocked.route(), Optional.of(unlocked.before()));
        };
    }

    private Layout reapplied(Edit edit) {
        return switch (edit) {
            case Edit.MovedStop moved -> layout.moving(stopAt(moved.stop()), moved.toRoute(), moved.toPosition());
            case Edit.TookOff took -> takingOff(layout, stopAt(took.stop()));
            case Edit.Placed placed ->
                layout.without(new StopKey(placed.order(), placed.attempt()))
                        .inserting(
                                placed.route(),
                                placed.position(),
                                new PlannedStop(
                                        placed.stop(),
                                        placed.order(),
                                        placed.attempt(),
                                        Optional.empty(),
                                        Optional.empty()));
            case Edit.Pinned pinned -> layout.pinning(pinned.order(), Optional.of(pinned.after()));
            case Edit.Unpinned unpinned -> layout.pinning(unpinned.order(), Optional.empty());
            case Edit.Locked locked -> layout.locking(locked.route(), Optional.of(locked.after()));
            case Edit.Unlocked unlocked -> layout.locking(unlocked.route(), Optional.empty());
        };
    }

    private PlannedStop stopAt(StopId stop) {
        return locate(stop).stop();
    }

    private static Layout takingOff(Layout from, PlannedStop stop) {
        return from.removing(stop.id())
                .adding(new DroppedOrder(stop.order(), stop.attempt(), new DropReason.RemovedByDispatcher()));
    }

    private static PlannedStop lateStop(LateFact fact, LateOutcome late) {
        return new PlannedStop(fact.stop(), fact.order(), fact.attempt(), Optional.empty(), Optional.of(late));
    }

    // ---- Revisions -----------------------------------------------------------------------------

    /**
     * Commits a change to a published or completed plan as its next revision, and raises {@link
     * PlanRevised}.
     */
    private void revise(
            Next change,
            RevisionCause cause,
            Optional<ProposalId> proposal,
            Optional<AccountId> by,
            Instant now,
            List<RouteId> changed,
            List<VehicleId> withdrawn) {
        RevisionNo number;
        if (status instanceof Status.Published published) {
            number = published.revision().next();
            change.status(new Status.Published(number));
        } else if (status instanceof Status.Completed completed) {
            number = completed.revision().next();
            change.status(new Status.Completed(number));
        } else {
            throw new AssertionError("only a published or completed plan is revised");
        }
        commit(change);
        pendingRevision = revisionOf(number, cause, proposal, by, now);
        int revision = number.value();
        raise(
                PlanRevised.class,
                id.value(),
                eventId -> new PlanRevised(eventId, now, id, date, revision, cause, changed, withdrawn));
    }

    private PlanRevision revisionOf(
            RevisionNo number,
            RevisionCause cause,
            Optional<ProposalId> proposal,
            Optional<AccountId> by,
            Instant now) {
        return new PlanRevision(
                number,
                now,
                cause,
                proposal,
                by,
                layout.routes().stream().map(Route::revision).toList());
    }

    // ---- Placing solved routes -----------------------------------------------------------------

    /**
     * Turns solved routes into the plan's routes (ADR-101):
     *
     * <ul>
     *   <li>a vehicle with a route in {@code current} keeps its ID, index and lock, and its confirmed
     *       return when its driver stays;
     *   <li>an order and attempt on any route of {@code stopSource} keeps its stop's ID and late mark;
     *   <li>the rest take new IDs and the lowest index that neither {@code keptAlongside} nor the other
     *       routes hold.
     * </ul>
     *
     * One route per vehicle and driver is checked before any index is handed out, so BR-39's
     * refusals come before {@link RouteIndexesUsedUp}.
     */
    private static List<Route> place(
            List<SolvedRoute> solved,
            List<Route> current,
            List<Route> stopSource,
            List<Route> keptAlongside,
            FreshIds ids,
            Function<VehicleId, Optional<Withdrawal>> withdrawalOf) {
        Map<StopKey, PlannedStop> existingStops = new HashMap<>();
        stopSource.forEach(route -> route.stops().forEach(stop -> existingStops.put(stop.key(), stop)));
        List<Route> provisional = new ArrayList<>();
        List<Boolean> needsIndex = new ArrayList<>();
        for (SolvedRoute route : solved) {
            List<PlannedStop> stops = route.stops().stream()
                    .map(stop -> {
                        PlannedStop existing = existingStops.get(new StopKey(stop.order(), stop.attempt()));
                        StopId stopId = existing != null ? existing.id() : StopId.of(ids.next());
                        Optional<LateOutcome> late = existing != null ? existing.late() : Optional.empty();
                        return new PlannedStop(stopId, stop.order(), stop.attempt(), Optional.of(stop.plan()), late);
                    })
                    .toList();
            Optional<Route> kept = current.stream()
                    .filter(candidate -> candidate.vehicle().equals(route.vehicle()))
                    .findFirst();
            RouteId routeId = kept.map(Route::id).orElseGet(() -> RouteId.of(ids.next()));
            Optional<DaySeconds> confirmed =
                    kept.filter(old -> old.driver().equals(route.driver())).flatMap(Route::confirmedReturn);
            provisional.add(new Route(
                    routeId,
                    kept.map(Route::index).orElse(RouteIndex.of(RouteIndex.LOWEST)),
                    route.vehicle(),
                    route.driver(),
                    kept.flatMap(Route::lock),
                    CheckState.CHECKED,
                    withdrawalOf.apply(route.vehicle()),
                    confirmed,
                    route.plan(),
                    stops));
            needsIndex.add(kept.isEmpty());
        }
        Map<VehicleId, RouteId> routed = new HashMap<>();
        for (Route route : provisional) {
            RouteId first = routed.putIfAbsent(route.vehicle(), route.id());
            if (first != null) {
                throw new RuleViolation(new VehicleAlreadyHasRoute(route.vehicle(), first));
            }
        }
        requireOneRoutePerVehicleAndDriver(provisional);
        Set<RouteIndex> taken = new HashSet<>();
        keptAlongside.forEach(route -> taken.add(route.index()));
        for (int i = 0; i < provisional.size(); i++) {
            if (!needsIndex.get(i)) {
                taken.add(provisional.get(i).index());
            }
        }
        List<Route> placed = new ArrayList<>();
        for (int i = 0; i < provisional.size(); i++) {
            Route route = provisional.get(i);
            if (needsIndex.get(i)) {
                RouteIndex index = lowestFree(taken);
                taken.add(index);
                route = route.withIndex(index);
            }
            placed.add(route);
        }
        return placed;
    }

    private static RouteIndex lowestFree(Set<RouteIndex> taken) {
        return IntStream.rangeClosed(RouteIndex.LOWEST, RouteIndex.HIGHEST)
                .mapToObj(RouteIndex::of)
                .filter(index -> !taken.contains(index))
                .findFirst()
                .orElseThrow(() -> new RuleViolation(new RouteIndexesUsedUp()));
    }

    /** Whether solved routes keep a locked route as its lock says (FR-147, ADR-101). */
    private static boolean keptBy(Route locked, List<SolvedRoute> solved) {
        List<StopKey> keys = locked.keys();
        Predicate<SolvedRoute> sameSequence =
                route -> keysOf(List.of(route), List.of()).equals(keys);
        RouteLock lock = locked.lock().orElseThrow();
        return switch (lock) {
            case WHOLE_ROUTE ->
                solved.stream()
                        .anyMatch(route -> route.vehicle().equals(locked.vehicle())
                                && route.driver().equals(locked.driver())
                                && sameSequence.test(route));
            case STOP_SEQUENCE -> solved.stream().anyMatch(sameSequence);
        };
    }

    /**
     * Gives each lock of the current routes to the route that kept it: a whole-route lock stays with
     * its vehicle's route, and a sequence lock goes to the route that now serves the sequence.
     */
    private List<Route> carryLocks(List<Route> placed) {
        List<Route> result = new ArrayList<>(
                placed.stream().map(route -> route.withLock(Optional.empty())).toList());
        for (Route locked : layout.routes()) {
            if (locked.lock().isEmpty()) {
                continue;
            }
            RouteLock lock = locked.lock().get();
            for (int position = 0; position < result.size(); position++) {
                Route candidate = result.get(position);
                boolean holds = lock == RouteLock.WHOLE_ROUTE
                        ? candidate.id().equals(locked.id())
                        : candidate.keys().equals(locked.keys());
                if (holds) {
                    result.set(position, candidate.withLock(Optional.of(lock)));
                }
            }
        }
        return result;
    }

    private static List<StopKey> keysOf(List<SolvedRoute> routes, List<DroppedOrder> dropped) {
        List<StopKey> keys = new ArrayList<>();
        routes.forEach(route -> route.stops().forEach(stop -> keys.add(new StopKey(stop.order(), stop.attempt()))));
        dropped.forEach(drop -> keys.add(new StopKey(drop.order(), drop.attempt())));
        return keys;
    }

    // ---- Accepting a proposal ------------------------------------------------------------------

    /**
     * The routes of vehicles the proposal withdraws but gives no route: each stays, withdrawn, without
     * the orders the proposal serves or drops, so no order is held twice and its index stays reserved.
     */
    private static List<Route> uncoveredWithdrawals(ReoptimizationProposalSnapshot proposal, List<Route> active) {
        Set<VehicleId> routed = new HashSet<>();
        proposal.routes().forEach(route -> routed.add(route.vehicle()));
        Set<StopKey> taken = new HashSet<>(keysOf(proposal.routes(), proposal.dropped()));
        return active.stream()
                .filter(route -> proposal.withdraws(route.vehicle()) && !routed.contains(route.vehicle()))
                .map(route -> route.withWithdrawal(withdrawalFor(proposal, route.vehicle()))
                        .withStops(route.stops().stream()
                                .filter(stop -> !taken.contains(stop.key()))
                                .toList()))
                .toList();
    }

    /**
     * The withdrawn routes a proposal routes again: a vehicle the proposal withdraws, with no active
     * route but a route withdrawn earlier, keeps that route's ID, index and stops (ADR-101).
     */
    private static List<Route> rewithdrawnRoutes(
            ReoptimizationProposalSnapshot proposal, List<Route> before, List<Route> covered) {
        List<Route> matched = new ArrayList<>();
        for (SolvedRoute route : proposal.routes()) {
            VehicleId vehicle = route.vehicle();
            boolean hasActive =
                    covered.stream().anyMatch(candidate -> candidate.vehicle().equals(vehicle));
            if (!hasActive && proposal.withdraws(vehicle)) {
                before.stream()
                        .filter(candidate ->
                                candidate.withdrawn() && candidate.vehicle().equals(vehicle))
                        .reduce((first, last) -> last)
                        .filter(found -> !matched.contains(found))
                        .ifPresent(matched::add);
            }
        }
        return matched;
    }

    /** BR-07 over the layout a change would leave, which a caller's input can still break. */
    private static void requireEachOrderOnce(Layout after) {
        Set<StopKey> seen = new HashSet<>();
        for (StopKey key : after.keys()) {
            if (!seen.add(key)) {
                throw new RuleViolation(new OrderAlreadyInPlan(key.order(), key.attempt()));
            }
        }
    }

    /** The changes since the proposal's basis that it moves, drops or relies on (BR-25, BR-82, BR-83). */
    private List<BasisChange> changesSince(
            ReoptimizationProposalSnapshot proposal, ChangesSinceBasis changes, RevisionNo current) {
        List<BasisChange> found = new ArrayList<>();
        for (int revision = proposal.basis().revision().value() + 1; revision <= current.value(); revision++) {
            found.add(new BasisChange.RevisionMade(RevisionNo.of(revision)));
        }
        for (StopId stop : changes.reportedStops()) {
            layout.locate(stop)
                    .filter(at -> movesOrDrops(proposal, at))
                    .ifPresent(at -> found.add(new BasisChange.ReportedStop(stop)));
        }
        Set<OrderId> relied = new LinkedHashSet<>();
        keysOf(proposal.routes(), proposal.dropped()).forEach(key -> relied.add(key.order()));
        Set<VehicleId> vehicles = new HashSet<>();
        Set<AccountId> drivers = new HashSet<>();
        proposal.routes().forEach(route -> {
            vehicles.add(route.vehicle());
            drivers.add(route.driver());
        });
        changes.cancelledOrders().stream()
                .filter(relied::contains)
                .forEach(order -> found.add(new BasisChange.CancelledOrder(order)));
        changes.changedOrders().stream()
                .filter(relied::contains)
                .forEach(order -> found.add(new BasisChange.ChangedOrder(order)));
        changes.changedVehicles().stream()
                .filter(vehicles::contains)
                .forEach(vehicle -> found.add(new BasisChange.ChangedVehicle(vehicle)));
        changes.changedDrivers().stream()
                .filter(drivers::contains)
                .forEach(driver -> found.add(new BasisChange.ChangedDriver(driver)));
        return found;
    }

    /**
     * Whether the proposal moves or drops a stop: it stays put only on the same vehicle, after the
     * same stops in the same order (BR-83, ADR-101).
     */
    private static boolean movesOrDrops(ReoptimizationProposalSnapshot proposal, Located at) {
        StopKey key = at.stop().key();
        List<StopKey> before = at.route().keys().subList(0, at.position());
        for (SolvedRoute route : proposal.routes()) {
            List<StopKey> keys = keysOf(List.of(route), List.of());
            int position = keys.indexOf(key);
            if (position >= 0) {
                return !route.vehicle().equals(at.route().vehicle())
                        || !keys.subList(0, position).equals(before);
            }
        }
        return true;
    }

    private static List<VehicleId> unavailableVehicles(
            ReoptimizationProposalSnapshot proposal, ChangesSinceBasis changes) {
        Set<VehicleId> unavailable = new LinkedHashSet<>();
        for (SolvedRoute route : proposal.routes()) {
            if (proposal.withdraws(route.vehicle())) {
                continue;
            }
            boolean meets = changes.standing().stream()
                    .anyMatch(standing -> standing.vehicle().equals(route.vehicle()) && standing.meetsBr20());
            if (!meets) {
                unavailable.add(route.vehicle());
            }
        }
        return List.copyOf(unavailable);
    }

    private static Optional<Withdrawal> withdrawalFor(ReoptimizationProposalSnapshot proposal, VehicleId vehicle) {
        return proposal.withdrawn().stream()
                .filter(withdrawal -> withdrawal.vehicle().equals(vehicle))
                .map(withdrawal -> new Withdrawal(withdrawal.cause()))
                .findFirst();
    }

    /** The routes whose driver, stops or withdrawal differ, or that joined or left, in plan order. */
    private static List<RouteId> changedRoutes(List<Route> before, List<Route> after) {
        List<RouteId> changed = new ArrayList<>();
        for (Route route : after) {
            Optional<Route> old = before.stream()
                    .filter(candidate -> candidate.id().equals(route.id()))
                    .findFirst();
            boolean same = old.isPresent()
                    && old.get().driver().equals(route.driver())
                    && old.get().stopIds().equals(route.stopIds())
                    && old.get().withdrawal().equals(route.withdrawal());
            if (!same) {
                changed.add(route.id());
            }
        }
        for (Route route : before) {
            if (after.stream().noneMatch(candidate -> candidate.id().equals(route.id()))) {
                changed.add(route.id());
            }
        }
        return changed;
    }

    private static List<RouteId> routeIds(List<Route> routes) {
        return routes.stream().map(Route::id).toList();
    }

    private static List<Edit> appended(List<Edit> edits, Edit edit) {
        List<Edit> next = new ArrayList<>(edits);
        next.add(edit);
        return List.copyOf(next);
    }

    // ---- Publishing checks ---------------------------------------------------------------------

    private List<PlanningError> unstaffed(List<RouteStaffing> staffing) {
        return layout.routes().stream()
                .filter(route -> staffing.stream()
                        .noneMatch(answer -> answer.vehicle().equals(route.vehicle())
                                && answer.driver().equals(route.driver())
                                && answer.assignable()))
                .<PlanningError>map(route -> new RouteNotStaffed(route.id(), route.vehicle()))
                .toList();
    }

    /** Step 2's refusals: every order not accounted for, then every order changed since the solve. */
    private List<PlanningError> unaccounted(List<DateOrder> orders) {
        AppliedSolve solve = Objects.requireNonNull(applied, "a ready plan has an applied solve");
        Set<StopKey> held = new HashSet<>(layout.keys());
        List<PlanningError> notAccounted = new ArrayList<>();
        Set<OrderId> changed = new LinkedHashSet<>();
        Set<OrderId> ofTheDate = new HashSet<>();
        for (DateOrder order : orders) {
            ofTheDate.add(order.order());
            if (!held.contains(new StopKey(order.order(), order.attempt()))) {
                notAccounted.add(new OrderNotAccounted(order.order(), order.attempt()));
            }
            if (solve.changedDuring(order)) {
                changed.add(order.order());
            }
        }
        for (StopKey key : layout.keys()) {
            if (!ofTheDate.contains(key.order())) {
                changed.add(key.order());
            }
        }
        List<PlanningError> refusals = new ArrayList<>(notAccounted);
        changed.forEach(order -> refusals.add(new OrderChangedSinceSolve(order)));
        return refusals;
    }
}
