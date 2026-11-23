package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.BasisChange;
import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.PlanningError;
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
import com.dispatchflow.planning.domain.PublishingStep;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.basis.ChangesSinceBasis;
import com.dispatchflow.planning.domain.basis.SolveBasis;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.planning.domain.plan.PlanLayout.LaidOutRoute;
import com.dispatchflow.planning.domain.plan.PlanLayout.LaidOutStop;
import com.dispatchflow.planning.domain.reconciliation.LateFact;
import com.dispatchflow.planning.domain.reconciliation.LateOutcome;
import com.dispatchflow.planning.domain.reconciliation.Reconciliation;
import com.dispatchflow.planning.domain.reconciliation.ReconciliationKind;
import com.dispatchflow.planning.domain.revision.PlanRevision;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.RoutePlan;
import com.dispatchflow.planning.domain.solution.RouteTimes;
import com.dispatchflow.planning.domain.solution.SolveResult;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.StopPlan;
import com.dispatchflow.planning.domain.solution.StopTiming;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.domain.solution.Withdrawal;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.PlanCancelled;
import com.dispatchflow.planning.events.PlanPublished;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.planning.events.RevisionCause;
import com.dispatchflow.planning.events.RouteHandedOver;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.Attempt;
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
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * <p>Every command checks every rule before it changes any field, so a refused call leaves the state
 * and the pending events as they were. It then changes the fields in place and ends by checking the
 * state it left against the invariants at rest that the load check runs, so no command can leave a
 * state that would not load again (ADR-126 decisions 8 and 11). The rules no document settled are
 * ADR-101's, and the readings ADR-112 records.
 *
 * <p>It is mapped for JPA (ADR-126, ADR-127): its key is its {@code PlanId}'s UUID; its status is a
 * kind beside the basis, revision and time each status keeps, and its applied solve three nullable
 * columns. Its routes are {@link Route} entities and their stops {@link PlannedStop} entities, in
 * order; the plan's own collection of stops creates and deletes them, so a stop that changes routes
 * is never deleted (ADR-127 decision 11). Its drops, excluded vehicles, pins, violations and both journals are a table
 * each. A command changes the collections Hibernate loaded and never assigns new ones. The revision a change makes
 * is not part of the plan's rows: the repository appends it as rows of its own when it saves the
 * plan, and never loads it again (ADR-059). Only Hibernate rebuilds a stored plan, and {@link
 * #checkStored} refuses one that breaks an invariant at rest (ADR-126 decision 8).
 */
@Entity
@Table(schema = "planning", name = "plan")
public class Plan extends AggregateRoot {

    /**
     * The most edits the undo and redo journals hold together; an edit past it forgets the oldest
     * (FR-145, ADR-112).
     */
    static final int JOURNAL_LIMIT = 100;

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

    /** Which {@link Status} a plan is in. */
    private enum StatusKind {
        DRAFT,
        SOLVING,
        READY,
        PUBLISHED,
        COMPLETED,
        CANCELLED,
        ABANDONED
    }

    /** The direction an {@link Edit} is made in: as the dispatcher made it, or undone. */
    private static final boolean FORWARD = true;

    private static final boolean BACKWARD = false;

    @Id
    private UUID id;

    @Column(name = "delivery_date")
    private DeliveryDate date;

    private Instant openedAt;

    @Enumerated(EnumType.STRING)
    private StatusKind status;

    @Column(name = "solving_job_id")
    private @Nullable JobId solvingJob;

    private @Nullable Instant solvingRequestedAt;

    private @Nullable RevisionNo revision;

    private @Nullable Instant cancelledAt;

    @Column(name = "applied_job_id")
    private @Nullable JobId appliedJob;

    private @Nullable Instant appliedRequestedAt;

    private @Nullable Instant appliedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "plan_id", nullable = false)
    @OrderColumn(name = "position", nullable = false)
    private List<Route> routes = new ArrayList<>();

    // Every stop on any of the routes, unordered: it alone creates and deletes the stops, so a stop
    // that changes routes stays in it and is never an orphan (ADR-127 decision 11). A route's list
    // holds only which stops it serves, in order (see Route.stops). The migration keeps a stop's
    // plan its route's plan.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "plan_id", nullable = false)
    private Set<PlannedStop> stops = new HashSet<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "plan_drop", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredDroppedOrder> dropped = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "plan_excluded_vehicle", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<ExcludedVehicle> excluded = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "plan_pin", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredPin> pins = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "plan_violation", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredViolation> violations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "plan_undo", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredEdit> undo = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "plan_redo", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredEdit> redo = new ArrayList<>();

    // The last command's revision, which the repository appends as rows of their own when it saves
    // the plan. Every command that returns sets it, to none when it made no revision, so saving the
    // plan again never appends one revision twice; a loaded plan has none.
    @Transient
    private @Nullable PlanRevision pendingRevision;

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected Plan() {}

    private Plan(PlanId id, DeliveryDate date, Instant openedAt) {
        super();
        this.id = id.value();
        this.date = date;
        this.openedAt = openedAt;
        this.status = StatusKind.DRAFT;
    }

    // ---- Creation and the load check -----------------------------------------------------------

    /**
     * Opens a plan for a date, in draft (FR-15).
     *
     * @param id the new plan's ID
     * @param date its delivery date
     * @param plansOnDate the date's uncancelled plan, as the use case read it
     * @param today the current date in {@code Asia/Jerusalem}
     * @param now the time of opening
     * @return the plan, never saved
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
        return new Plan(id, date, now);
    }

    /**
     * Checks the plan as Hibernate loaded it, which alone rebuilds a stored plan (ADR-126 decision
     * 8). It rebuilds every stored value and checks the invariants that hold at rest, never one that
     * needs {@code today} or an outside fact.
     *
     * @throws StoredStateInvalid when the stored state breaks an invariant: a status's fields don't
     *     match it, an order and attempt appears twice (BR-07), a vehicle or driver drives two routes
     *     (BR-39), an ID or route index repeats, an order has two pins, the drops or pins are out of
     *     the order the commands keep them in, a violation or journal entry names a route or stop
     *     the plan doesn't hold as it should, or a stored value is refused
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(Plan.class, broken);
        });
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
        requireDraftOrReady();
        requireNotPast(today);
        SolveJob job = SolveJob.queue(jobId, id(), Lane.DAILY, solver, limit, seed, Optional.empty(), capacity, now);
        forgetPendingRevision();
        storeStatus(new Status.Solving(new SolveBasis(jobId, now)));
        requireInvariants();
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
                id(),
                Lane.REOPTIMIZATION,
                solver,
                SolveLimit.REOPTIMIZATION,
                seed,
                Optional.of(published.revision()),
                capacity,
                now);
    }

    /**
     * Applies the result of the job the plan is solving for: its routes, drops and excluded vehicles
     * replace the plan's, and the plan becomes ready (BR-15, BR-59, FR-197). A result from any other
     * job, or in any other state, changes nothing, so the listener's repeats are harmless; that
     * includes an abandoned plan (ADR-101).
     *
     * <p>A vehicle keeps its route's ID and index, an order and attempt keeps its stop's ID, and a
     * new vehicle takes the lowest free index (ADR-101). A locked route must come back as locked
     * (FR-147); the journal is cleared (FR-145). The worker asks {@link #locksBrokenBy} before the
     * job succeeds, so the lock check here is a backstop (ADR-112). A {@code now} before the
     * request counts as the request's time (ADR-112).
     *
     * @param job the ended job, whose result the plan applies
     * @param ids new IDs for new routes and stops: it draws at most one per route of the result and one
     *     per stop of the result, so the use case supplies that many (ADR-112)
     * @param now when the plan applies it
     * @throws RuleViolation with {@link RouteLocked} when the result changes a locked route, {@link
     *     OrderAlreadyInPlan} (BR-07), or {@link VehicleAlreadyHasRoute} or {@link
     *     DriverAlreadyHasRoute} (BR-39); each means a check upstream was skipped
     */
    public void applySolveResult(SolveJob job, FreshIds ids, Instant now) {
        Optional<SolveResult> kept = job.result();
        if (!(status() instanceof Status.Solving solving)
                || !solving.pending().job().equals(job.id())
                || !job.plan().equals(id())
                || kept.isEmpty()) {
            forgetPendingRevision();
            return;
        }
        SolveResult result = kept.get();
        Optional<Route> broken = locksBrokenBy(result.routes()).stream().findFirst();
        if (broken.isPresent()) {
            throw new RuleViolation(new RouteLocked(broken.get().id()));
        }
        requireSingleAssignment(result.routes(), result.dropped());
        List<PlacedRoute> before = placedRoutes();
        List<PlacedRoute> placed =
                place(result.routes(), before, before, List.of(), ids, vehicle -> Optional.empty(), route -> List.of());
        List<PlacedRoute> next = carryLocks(before, placed);
        Instant solvedAt = TimeOrder.notBefore(now, solving.pending().requestedAt());
        forgetPendingRevision();
        storeStatus(new Status.Ready());
        storeApplied(Optional.of(new AppliedSolve(solving.pending(), solvedAt)));
        layRoutes(next);
        storeDrops(result.dropped());
        excluded.clear();
        excluded.addAll(result.excluded());
        violations.clear();
        clearJournal();
        requireInvariants();
    }

    /**
     * The job the plan is solving for failed: the plan returns to ready if it was solved before, and
     * otherwise to draft, with its routes as they were (BR-57, BR-59). Any other job, or any other
     * state, changes nothing; that includes an abandoned plan (ADR-053).
     *
     * @param jobId the job that failed
     */
    public void solveFailed(JobId jobId) {
        forgetPendingRevision();
        if (status() instanceof Status.Solving solving
                && solving.pending().job().equals(jobId)) {
            storeStatus(applied().isEmpty() ? new Status.Draft() : new Status.Ready());
            requireInvariants();
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
        commitEdit(moving(stop, toRoute, position), evaluation);
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
        commitEdit(takingOff(stop), evaluation);
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
        commitEdit(placing(order, attempt, toRoute, position, newStop), evaluation);
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
        commitPinEdit(pinning(order, target), evaluation);
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
        commitPinEdit(unpinning(order), evaluation);
    }

    /**
     * Locks a route against edits and daily solves, replacing any lock it had (FR-147). A lock
     * changes no route, so it needs no evaluation. Locking a route with the lock it already has
     * changes nothing and journals nothing (ADR-112). Publishing clears every lock.
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
        forgetPendingRevision();
        if (locked.lock().equals(Optional.of(lock))) {
            return;
        }
        commitLockEdit(new Edit.Locked(route, locked.lock(), lock));
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
        forgetPendingRevision();
        commitLockEdit(new Edit.Unlocked(route, before));
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
        Edit last = lastUndo();
        PlanLayout after = viewAfter(last, BACKWARD);
        requireCurrentAndAvailable(evaluation);
        requireDescribes(evaluation, after);
        forgetPendingRevision();
        change(last, BACKWARD, after);
        judge(evaluation, true);
        storeViolations(evaluation.violations());
        undo.remove(undo.size() - 1);
        redo.add(new StoredEdit(last));
        requireInvariants();
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
        Edit last = lastRedo();
        PlanLayout after = viewAfter(last, FORWARD);
        requireCurrentAndAvailable(evaluation);
        requireDescribes(evaluation, after);
        forgetPendingRevision();
        change(last, FORWARD, after);
        judge(evaluation, true);
        storeViolations(evaluation.violations());
        redo.remove(redo.size() - 1);
        undo.add(new StoredEdit(last));
        requireInvariants();
    }

    // ---- Publishing and the published plan -----------------------------------------------------

    /**
     * Publishes the plan as revision 1, with the re-checked times (FR-26, BR-68). The re-check must
     * describe this version and layout before any step runs. Then the four checks of BR-72 run in
     * order, and the first that refuses lists every refusal it found. Publishing clears every lock,
     * because a lock binds daily solves only and a re-optimization takes none (ADR-105, ADR-112).
     * Raises {@link PlanPublished}.
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
        if (recheck.available()) {
            requireDescribes(recheck, layout());
        }
        RuleViolations.throwIfAny(new PublishingRefused(PublishingStep.STAFFING), unstaffed(checks.staffing()));
        RuleViolations.throwIfAny(new PublishingRefused(PublishingStep.ORDERS), unaccounted(checks.orders()));
        RuleViolations.throwIfAny(
                new PublishingRefused(PublishingStep.VIOLATIONS),
                violations().stream()
                        .map(violation -> new OpenViolation(violation.rule(), violation.route(), violation.order()))
                        .toList());
        if (!routes.isEmpty()) {
            List<PlanningError> refusals = recheck.available()
                    ? recheck.violations().stream()
                            .<PlanningError>map(violation ->
                                    new RecheckFailed(violation.rule(), violation.route(), violation.order()))
                            .toList()
                    : List.of(new RecheckUnavailable());
            RuleViolations.throwIfAny(new PublishingRefused(PublishingStep.RECHECK), refusals);
        }
        if (recheck.available()) {
            judge(recheck, true);
        }
        routes.forEach(route -> route.lockAs(Optional.empty()));
        clearJournal();
        storeStatus(new Status.Published(RevisionNo.FIRST));
        requireInvariants();
        pendingRevision = revisionOf(RevisionNo.FIRST, RevisionCause.PUBLISHED, Optional.empty(), Optional.of(by), now);
        PlanId plan = id();
        List<RouteId> published = routeIds();
        raise(PlanPublished.class, id, eventId -> new PlanPublished(eventId, now, plan, date, 1, published));
    }

    /**
     * Accepts a re-optimization: its routes, drops and excluded vehicles replace the plan's, as a new
     * revision that cites it (FR-36, BR-62, ADR-056, FR-197). It takes the proposal itself, which only
     * a succeeded job or persistence builds, so only a validated solution reaches the plan (BR-15).
     * Raises {@link PlanRevised}.
     *
     * <p>A proposal holds no stop already reported done or in progress (BR-10, BR-108), so every route
     * of the plan in force stays, and keeps the stops the proposal neither serves nor drops ahead of
     * the proposal's stops: its reported prefix (BR-83, FR-268, ADR-112). In detail:
     *
     * <ul>
     *   <li>A vehicle keeps its route's ID and index, and its confirmed return when it keeps its
     *       driver; an order and attempt keeps its stop's ID, from any route, withdrawn ones included.
     *   <li>A route the proposal gives no route stays with its prefix, or with no stops left, and is
     *       marked withdrawn when the proposal withdraws its vehicle (FR-271). When the proposal takes
     *       stops from it, it is not yet checked (FR-395), and with no stops left it has no times.
     *   <li>A vehicle withdrawn earlier that the proposal withdraws again and routes keeps that
     *       withdrawn route: its ID, index and stops, and takes the proposal's cause.
     *   <li>Any other route withdrawn earlier stays as it is (FR-268), and an order it holds that the
     *       proposal serves or drops elsewhere refuses the acceptance (BR-07, ADR-101).
     *   <li>The proposal's drops replace the plan's. A drop of the plan that the proposal neither
     *       serves nor drops leaves when the proposal holds a later attempt of its order, as after a
     *       put-back onto the same date (ADR-115), and is otherwise carried forward as it was, as
     *       after a put-back onto another date (ADR-112).
     *   <li>So every other order and attempt the plan held is still held, on a route or dropped
     *       (BR-07).
     *   <li>The routes keep their order in the plan, and a new route joins the end. No route takes a
     *       lock, because a published plan holds none.
     * </ul>
     *
     * <p>A stop reported since the basis stands when the route as accepted keeps it on its vehicle
     * after the same stops (BR-83, ADR-101). {@link PlanRevised} names each vehicle newly withdrawn
     * with its cause, and the orders the plan held under no attempt before (FR-39, FR-271, ADR-112).
     *
     * @param proposal the proposal
     * @param changes what was recorded since its basis
     * @param ids new IDs for new routes and stops: it draws at most one per route of the proposal and
     *     one per stop of the proposal, so the use case supplies that many (ADR-112)
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
        if (!proposal.plan().equals(id())) {
            throw new RuleViolation(new ProposalForAnotherPlan(proposal.id(), proposal.plan()));
        }
        if (proposal.state() instanceof ReoptimizationProposal.State.Declined) {
            throw new RuleViolation(new ProposalDeclined(proposal.id()));
        }
        Set<StopKey> taken = new HashSet<>(keysOf(proposal.routes(), proposal.dropped()));
        List<BasisChange> outdated = changesSince(proposal, taken, changes, published.revision());
        if (!outdated.isEmpty()) {
            throw new RuleViolation(new ProposalOutdated(proposal.id(), outdated));
        }
        List<VehicleId> unavailable = unavailableVehicles(proposal, changes);
        if (!unavailable.isEmpty()) {
            throw new RuleViolation(new VehicleUnavailable(unavailable));
        }
        requireSingleAssignment(proposal.routes(), proposal.dropped());
        List<PlacedRoute> before = placedRoutes();
        Set<VehicleId> routed = new HashSet<>();
        proposal.routes().forEach(route -> routed.add(route.vehicle()));
        List<PlacedRoute> active =
                before.stream().filter(route -> !route.withdrawn()).toList();
        List<PlacedRoute> covered = active.stream()
                .filter(route -> routed.contains(route.vehicle()))
                .toList();
        List<PlacedRoute> leftOut = active.stream()
                .filter(route -> !routed.contains(route.vehicle()))
                .map(route -> leftOutRoute(route, withdrawalFor(proposal, route.vehicle()), keptStops(route, taken)))
                .toList();
        List<PlacedRoute> rewithdrawn = rewithdrawnRoutes(proposal, before, covered);
        List<PlacedRoute> matchable = new ArrayList<>(covered);
        matchable.addAll(rewithdrawn);
        List<PlacedRoute> alongside = new ArrayList<>(before.stream()
                .filter(route -> route.withdrawn() && !rewithdrawn.contains(route))
                .toList());
        alongside.addAll(leftOut);
        List<PlacedRoute> placed = place(
                proposal.routes(),
                matchable,
                before,
                alongside,
                ids,
                vehicle -> withdrawalFor(proposal, vehicle),
                route -> keptStops(route, taken));
        List<PlacedRoute> next = inPlanOrder(before, placed, leftOut);
        Set<StopKey> superseded = supersededDrops(taken);
        List<DroppedOrder> dropsAfter = droppedAfter(proposal, taken, superseded).stream()
                .sorted(PlanLayout.DROP_ORDER)
                .toList();
        List<StopKey> keysAfter = heldKeys(next, dropsAfter);
        requireEachOrderOnce(keysAfter);
        requireNothingLost(keysAfter, superseded);
        List<VehicleWithdrawal> newlyWithdrawn = next.stream()
                .filter(route -> before.stream().noneMatch(old -> old.id().equals(route.id()) && old.withdrawn()))
                .flatMap(route -> route.withdrawal().stream()
                        .map(withdrawal -> new VehicleWithdrawal(route.vehicle(), withdrawal.cause())))
                .toList();
        List<RouteId> changed = changedRoutes(before, next);
        List<OrderId> added = addedOrders(keysAfter);
        forgetPendingRevision();
        layRoutes(next);
        storeDrops(dropsAfter);
        excluded.clear();
        excluded.addAll(proposal.excluded());
        violations.clear();
        revise(
                RevisionCause.REOPTIMIZATION_ACCEPTED,
                Optional.of(proposal.id()),
                Optional.of(by),
                now,
                changed,
                newlyWithdrawn,
                added);
    }

    /**
     * Hands a route of the published plan to another driver; its stops and times stay, and its
     * reported stops keep their deliveries (FR-55, FR-192, FR-303). Raises {@link PlanRevised}, then
     * {@link RouteHandedOver}, so the roster follows (FR-191).
     *
     * <p>With an available evaluation, the route is checked. The evaluation must have judged the
     * route with the new driver, and the validator judges a shift by the vehicle's rostered driver,
     * so the use case asks for it with the vehicle rostered to the new driver (ADR-112). A broken
     * shift (BR-06) needs the dispatcher's confirmation. A confirmed one sets the route's confirmed
     * return to the evaluated return (FR-304, FR-394), and waives the route's BR-06 and its BR-105
     * break reserve, which the same late return breaks (ADR-112). With no evaluation, or an
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
     *     {@link RouteWithdrawn}, {@link DriverAlreadyHasRoute} (FR-302), {@link StaleEvaluation},
     *     {@link NotAnActiveDriver}, {@link DriverNotAvailable}, or {@link BreakNotConfirmed}
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
        Route handed = route(route);
        AccountId driver = standing.driver();
        PlanLayout after = handingOver(route, driver);
        evaluation.ifPresent(this::requireCurrent);
        if (!standing.activeDriver()) {
            throw new RuleViolation(new NotAnActiveDriver(driver));
        }
        if (!standing.available()) {
            throw new RuleViolation(new DriverNotAvailable(driver));
        }
        List<Violation> nextViolations = violations();
        Optional<Evaluation> judging = evaluation.filter(Evaluation::available);
        Optional<SolveTime> confirmed = Optional.empty();
        if (judging.isPresent()) {
            Evaluation judged = judging.get();
            requireDescribes(judged, after);
            boolean breaks = judged.breaksShift(route);
            if (breaks && !breakConfirmed) {
                throw new RuleViolation(new BreakNotConfirmed(route, driver));
            }
            confirmed = breaks ? judged.returnOf(route) : Optional.empty();
            nextViolations = judged.violations().stream()
                    .filter(violation -> !(breaks
                            && (violation.breaks(RuleCode.SHIFTS, route)
                                    || violation.breaks(RuleCode.BREAK_RESERVE, route))))
                    .toList();
        }
        AccountId from = handed.driver();
        VehicleId vehicle = handed.vehicle();
        forgetPendingRevision();
        handed.driveBy(driver);
        if (judging.isPresent()) {
            judge(judging.get(), false);
        } else {
            handed.checkAs(CheckState.NOT_YET_CHECKED);
        }
        handed.confirmReturnAt(confirmed);
        storeViolations(nextViolations);
        revise(RevisionCause.HANDED_OVER, Optional.empty(), Optional.of(by), now, List.of(route), List.of(), List.of());
        PlanId plan = id();
        raise(
                RouteHandedOver.class,
                id,
                eventId -> new RouteHandedOver(eventId, now, plan, date, route, vehicle, from, driver));
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
        forgetPendingRevision();
        boolean ready = status == StatusKind.READY;
        if (!ready && status != StatusKind.PUBLISHED) {
            return;
        }
        StopKey key = new StopKey(order, attempt);
        Optional<Located> located = locate(key);
        List<RouteId> changed;
        if (located.isPresent()) {
            Route route = located.get().route();
            takeOff(located.get());
            route.checkAs(CheckState.NOT_YET_CHECKED);
            changed = List.of(route.id());
        } else if (drop(key).isPresent()) {
            removeDrop(key);
            changed = List.of();
        } else {
            return;
        }
        if (ready) {
            clearJournal();
            requireInvariants();
        } else {
            revise(cause.revisionCause(), Optional.empty(), Optional.empty(), now, changed, List.of(), List.of());
        }
    }

    /**
     * Takes a dropped order and attempt off the plan of its old date after it was put back onto
     * another date (FR-75, ADR-112). Its listener calls it with the attempt that was dropped or
     * failed, when Customers &amp; Orders announces a put-back to another date. The drop leaves at
     * once, as a date change takes an order off a ready plan (FR-316), and the journal is cleared
     * (FR-145).
     *
     * <p>Only a drop leaves. A stop of that attempt stays where it is, because a put-back stop can
     * only have failed, and the earlier stop's outcome stays as recorded (FR-258, BR-07). Only a ready
     * plan changes, so its publishing isn't refused for an order its date no longer has (BR-71). A
     * draft holds no orders. A published, completed or cancelled plan keeps the drop as the day's
     * history. A solving plan changes nothing either: its routes and drops wait for its new solve's
     * result, and publishing refuses an order the date no longer has until a solve leaves it out
     * (BR-71). An order the plan doesn't drop changes nothing, so a repeat is harmless (ADR-101).
     *
     * @param order the order
     * @param attempt the attempt that was dropped or failed
     */
    public void removePutBackOrder(OrderId order, Attempt attempt) {
        forgetPendingRevision();
        StopKey key = new StopKey(order, attempt);
        if (status == StatusKind.READY && drop(key).isPresent()) {
            removeDrop(key);
            clearJournal();
            requireInvariants();
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
        if (status == StatusKind.CANCELLED) {
            forgetPendingRevision();
            return Reconciliation.of(ReconciliationKind.PLAN_CANCELLED);
        }
        if (!(status == StatusKind.PUBLISHED || status == StatusKind.COMPLETED)) {
            throw new RuleViolation(new PlanNotPublished(id()));
        }
        RouteId servedOn = fact.servedOn();
        Route serving = route(servedOn);
        StopKey key = new StopKey(fact.order(), fact.attempt());
        Optional<Located> located = locate(fact.stop()).or(() -> locate(key));
        forgetPendingRevision();
        Set<RouteId> changed = new HashSet<>(List.of(servedOn));
        Reconciliation reconciliation;
        if (located.isPresent()) {
            Located at = located.get();
            if (at.route().id().equals(servedOn)) {
                return Reconciliation.of(ReconciliationKind.UNCHANGED);
            }
            PlannedStop moved = at.stop();
            at.route().remove(moved);
            moved.markLate(Optional.of(LateOutcome.SERVED_LATE));
            serving.append(moved);
            changed.add(at.route().id());
            reconciliation = Reconciliation.movedFrom(at.route().id());
        } else if (drop(key).isPresent()) {
            removeDrop(key);
            appendTo(serving, lateStop(fact, LateOutcome.SERVED_LATE));
            reconciliation = Reconciliation.of(ReconciliationKind.FROM_DROPPED);
        } else {
            appendTo(serving, lateStop(fact, LateOutcome.SERVED_AFTER_CANCELLATION));
            reconciliation = Reconciliation.of(ReconciliationKind.AFTER_CANCELLATION);
        }
        List<RouteId> changedInOrder =
                routeIds().stream().filter(changed::contains).toList();
        revise(
                RevisionCause.LATE_OUTCOME,
                Optional.empty(),
                Optional.empty(),
                now,
                changedInOrder,
                List.of(),
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
        if (!(status == StatusKind.READY || status == StatusKind.PUBLISHED)) {
            forgetPendingRevision();
            return;
        }
        requireCurrent(evaluation);
        if (!evaluation.available()) {
            forgetPendingRevision();
            return;
        }
        requireDescribes(evaluation, layout());
        forgetPendingRevision();
        judge(evaluation, false);
        storeViolations(evaluation.violations());
        requireInvariants();
    }

    /**
     * Cancels the published plan for a day that doesn't run, while no route has started and no stop
     * is reported (FR-336, BR-103). Its routes stay as they were, and cancelling makes no revision.
     * Raises {@link PlanCancelled}, so its drivers are alerted (FR-337).
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
        forgetPendingRevision();
        storeStatus(new Status.Cancelled(published.revision(), now));
        requireInvariants();
        PlanId plan = id();
        List<RouteId> cancelled = routeIds();
        raise(PlanCancelled.class, id, eventId -> new PlanCancelled(eventId, now, plan, date, cancelled));
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
        forgetPendingRevision();
        storeStatus(new Status.Completed(published.revision()));
        requireInvariants();
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
        if (!(status == StatusKind.DRAFT || status == StatusKind.SOLVING || status == StatusKind.READY)) {
            throw new RuleViolation(new PlanNotDraftSolvingOrReady(id()));
        }
        requireDateOver(today);
        forgetPendingRevision();
        clearJournal();
        storeStatus(new Status.Abandoned());
        requireInvariants();
    }

    // ---- Queries -------------------------------------------------------------------------------

    /**
     * The locks a daily solve's validated solution doesn't keep (FR-147, FR-238). The worker asks it
     * before the job succeeds, and fails the job with {@code SolveFailure.lockNotHonoured} when it
     * names any, so a result that breaks a lock never reaches {@link #applySolveResult} (ADR-112). A
     * whole-route lock keeps its vehicle, its driver and its stops in order; a sequence lock keeps
     * its stops in order on whichever one route serves them, and an empty sequence stays on its own
     * vehicle's route, empty.
     *
     * @param solution the solution the validator accepted
     * @return one {@link RuleCode#LOCKS} violation on each locked route the solution doesn't keep, in
     *     plan order; empty when it keeps every lock
     */
    public List<Violation> locksBrokenBy(ValidatedSolution solution) {
        return locksBrokenBy(solution.routes()).stream()
                .map(route -> Violation.onRoute(RuleCode.LOCKS, route.id()))
                .toList();
    }

    /**
     * The plan's routes, drops and pins as they are, which an evaluation for the publishing re-check
     * or the route check must describe (FR-395, BR-21).
     *
     * @return the layout, without times
     */
    public PlanLayout layout() {
        return new PlanLayout(
                routes.stream()
                        .map(route -> new LaidOutRoute(
                                route.id(),
                                route.vehicle(),
                                route.driver(),
                                route.withdrawn(),
                                route.stops().stream().map(Plan::laidOut).toList()))
                        .toList(),
                dropped(),
                pins());
    }

    /**
     * The plan as {@link #moveStop} would leave it, for the evaluation that command takes (ADR-101
     * point 15, DL §6.3). It changes nothing, and the command leaves exactly this layout (ADR-112).
     *
     * @param stop the stop
     * @param toRoute the route it goes to
     * @param position its position there, from 0, counted in the route without it
     * @return the layout after the move
     * @throws RuleViolation with {@link PlanNotReady}, {@link StopNotInPlan}, {@link RouteNotInPlan},
     *     {@link PositionOutOfRange}, or {@link RouteLocked} for either route, as the command would
     */
    public PlanLayout afterMove(StopId stop, RouteId toRoute, int position) {
        requireReady();
        return viewAfter(moving(stop, toRoute, position), FORWARD);
    }

    /**
     * The plan as {@link #takeOffRoute} would leave it, for the evaluation that command takes. It
     * changes nothing, and the command leaves exactly this layout (ADR-112).
     *
     * @param stop the stop
     * @return the layout with the stop dropped, as removed by the dispatcher
     * @throws RuleViolation with {@link PlanNotReady}, {@link StopNotInPlan}, or {@link RouteLocked},
     *     as the command would
     */
    public PlanLayout afterTakeOff(StopId stop) {
        requireReady();
        return viewAfter(takingOff(stop), FORWARD);
    }

    /**
     * The plan as {@link #placeDropped} would leave it, for the evaluation that command takes. It
     * changes nothing, and the command leaves exactly this layout (ADR-112).
     *
     * @param order the order
     * @param attempt its attempt
     * @param toRoute the route
     * @param position its position there, from 0
     * @param newStop the new stop's ID, which the command must then be given
     * @return the layout with the order on the route
     * @throws RuleViolation with {@link PlanNotReady}, {@link OrderNotDropped}, {@link
     *     OrderNotRoutable}, {@link RouteNotInPlan}, {@link PositionOutOfRange}, or {@link
     *     RouteLocked}, as the command would
     */
    public PlanLayout afterPlacing(OrderId order, Attempt attempt, RouteId toRoute, int position, StopId newStop) {
        requireReady();
        return viewAfter(placing(order, attempt, toRoute, position, newStop), FORWARD);
    }

    /**
     * The plan as {@link #pin} would leave it, for the evaluation that command takes. It changes
     * nothing, and the command leaves exactly this layout (ADR-112).
     *
     * @param order the order
     * @param target the vehicle or driver
     * @return the layout with the order pinned to the target, replacing any pin it had
     * @throws RuleViolation with {@link PlanNotDraftOrReady}, as the command would
     */
    public PlanLayout afterPin(OrderId order, PinTarget target) {
        requireDraftOrReady();
        return viewAfter(pinning(order, target), FORWARD);
    }

    /**
     * The plan as {@link #unpin} would leave it, for the evaluation that command takes. It changes
     * nothing, and the command leaves exactly this layout (ADR-112).
     *
     * @param order the order
     * @return the layout without the order's pin
     * @throws RuleViolation with {@link PlanNotDraftOrReady} or {@link OrderNotPinned}, as the command
     *     would
     */
    public PlanLayout afterUnpin(OrderId order) {
        requireDraftOrReady();
        return viewAfter(unpinning(order), FORWARD);
    }

    /**
     * The plan as {@link #undo} would leave it, for the evaluation that command takes. It changes
     * nothing, and the command leaves exactly this layout (ADR-112).
     *
     * @return the layout with the last edit undone
     * @throws RuleViolation with {@link PlanNotReady} or {@link NothingToUndo}, as the command would
     */
    public PlanLayout afterUndo() {
        requireReady();
        return viewAfter(lastUndo(), BACKWARD);
    }

    /**
     * The plan as {@link #redo} would leave it, for the evaluation that command takes. It changes
     * nothing, and the command leaves exactly this layout (ADR-112).
     *
     * @return the layout with the last undone edit made again
     * @throws RuleViolation with {@link PlanNotReady} or {@link NothingToRedo}, as the command would
     */
    public PlanLayout afterRedo() {
        requireReady();
        return viewAfter(lastRedo(), FORWARD);
    }

    /**
     * The plan as {@link #handOver} would leave it, for the evaluation that command takes; the
     * validator judges it with the vehicle rostered to the new driver (ADR-112). It changes nothing,
     * and the command leaves exactly this layout.
     *
     * @param route the route
     * @param driver the new driver
     * @return the layout with the route handed to the driver
     * @throws RuleViolation with {@link PlanNotPublished}, {@link RouteNotInPlan}, {@link
     *     RouteWithdrawn}, or {@link DriverAlreadyHasRoute} (FR-302), as the command would
     */
    public PlanLayout afterHandOver(RouteId route, AccountId driver) {
        requirePublished();
        return handingOver(route, driver);
    }

    /**
     * The plan's ID.
     *
     * @return the ID
     */
    public PlanId id() {
        return PlanId.of(id);
    }

    /**
     * The plan's delivery date, of which a date has at most one uncancelled plan (BR-34).
     *
     * @return the date
     */
    public DeliveryDate date() {
        return date;
    }

    /**
     * The plan's state.
     *
     * @return the state, with the basis, revision and time it keeps
     */
    public Status status() {
        return switch (status) {
            case DRAFT -> new Status.Draft();
            case SOLVING -> new Status.Solving(new SolveBasis(stored(solvingJob), stored(solvingRequestedAt)));
            case READY -> new Status.Ready();
            case PUBLISHED -> new Status.Published(stored(revision));
            case COMPLETED -> new Status.Completed(stored(revision));
            case CANCELLED -> new Status.Cancelled(stored(revision), stored(cancelledAt));
            case ABANDONED -> new Status.Abandoned();
        };
    }

    /**
     * The revision the last command made, which the repository appends when it saves the plan
     * (ADR-059). Every command sets it, so a save after a command that made none appends nothing.
     *
     * @return the revision, or empty when the last command made none, or the plan was just loaded
     */
    public Optional<PlanRevision> pendingRevision() {
        return Optional.ofNullable(pendingRevision);
    }

    // ---- The stored state, for the invariants --------------------------------------------------

    /** Its routes, in order, as the live entities. */
    List<Route> routes() {
        return Collections.unmodifiableList(routes);
    }

    /** When it was opened. */
    Instant openedAt() {
        return openedAt;
    }

    /** Every stop it keeps, which is every stop on its routes. */
    Set<PlannedStop> keptStops() {
        return Collections.unmodifiableSet(stops);
    }

    /** The solve its routes came from, which three nullable columns hold, all of them or none. */
    Optional<AppliedSolve> applied() {
        if (appliedJob == null || appliedRequestedAt == null || appliedAt == null) {
            return Optional.empty();
        }
        return Optional.of(new AppliedSolve(new SolveBasis(appliedJob, appliedRequestedAt), appliedAt));
    }

    /** Its drops, ordered by order ID and attempt. */
    List<DroppedOrder> dropped() {
        return dropped.stream().map(StoredDroppedOrder::droppedOrder).toList();
    }

    List<ExcludedVehicle> excluded() {
        return List.copyOf(excluded);
    }

    /** Its pins, ordered by order ID. */
    List<Pin> pins() {
        return pins.stream().map(StoredPin::pin).toList();
    }

    List<Violation> violations() {
        return violations.stream().map(StoredViolation::violation).toList();
    }

    /** The edits since the last solve, oldest first. */
    List<Edit> undo() {
        return undo.stream().map(StoredEdit::edit).toList();
    }

    /** The undone edits, oldest first. */
    List<Edit> redo() {
        return redo.stream().map(StoredEdit::edit).toList();
    }

    // ---- Storing the fields in place -----------------------------------------------------------

    /**
     * The one check of the stored fields, shared by the load check and every command's end check.
     * Rebuilding every stored value checks it on the way.
     */
    private Optional<String> brokenRule() {
        routes.forEach(route -> {
            route.plan();
            route.confirmedReturn();
            route.stops().forEach(PlannedStop::plan);
        });
        return PlanInvariants.brokenBy(this);
    }

    /**
     * Checks the state a command left against the invariants at rest (ADR-126 decision 11). Every
     * input was checked before the command changed anything, so a broken invariant here is a bug in
     * the command, not a refusal, and the error rolls its transaction back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a plan command left a state that breaks: " + broken);
        });
    }

    /** A command that returns made no revision unless it sets one after this. */
    private void forgetPendingRevision() {
        pendingRevision = null;
    }

    /** Sets the kind of a status and the basis, revision and time it keeps, in place. */
    private void storeStatus(Status next) {
        solvingJob = null;
        solvingRequestedAt = null;
        revision = null;
        cancelledAt = null;
        status = switch (next) {
            case Status.Draft draft -> StatusKind.DRAFT;
            case Status.Solving solving -> {
                solvingJob = solving.pending().job();
                solvingRequestedAt = solving.pending().requestedAt();
                yield StatusKind.SOLVING;
            }
            case Status.Ready ready -> StatusKind.READY;
            case Status.Published published -> {
                revision = published.revision();
                yield StatusKind.PUBLISHED;
            }
            case Status.Completed completed -> {
                revision = completed.revision();
                yield StatusKind.COMPLETED;
            }
            case Status.Cancelled cancelled -> {
                revision = cancelled.revision();
                cancelledAt = cancelled.at();
                yield StatusKind.CANCELLED;
            }
            case Status.Abandoned abandoned -> StatusKind.ABANDONED;
        };
    }

    private void storeApplied(Optional<AppliedSolve> next) {
        appliedJob = next.map(solve -> solve.basis().job()).orElse(null);
        appliedRequestedAt = next.map(solve -> solve.basis().requestedAt()).orElse(null);
        appliedAt = next.map(AppliedSolve::appliedAt).orElse(null);
    }

    /** Replaces the drops in the list Hibernate loaded, in {@link PlanLayout#DROP_ORDER}. */
    private void storeDrops(List<DroppedOrder> next) {
        dropped.clear();
        next.stream().sorted(PlanLayout.DROP_ORDER).map(StoredDroppedOrder::new).forEach(dropped::add);
    }

    /** Adds a drop where {@link PlanLayout#DROP_ORDER} puts it, after any drop it ties with. */
    private void addDrop(DroppedOrder drop) {
        List<DroppedOrder> current = dropped();
        int at = 0;
        while (at < current.size() && PlanLayout.DROP_ORDER.compare(current.get(at), drop) <= 0) {
            at++;
        }
        dropped.add(at, new StoredDroppedOrder(drop));
    }

    private void removeDrop(StopKey key) {
        List<DroppedOrder> current = dropped();
        for (int at = current.size() - 1; at >= 0; at--) {
            DroppedOrder drop = current.get(at);
            if (new StopKey(drop.order(), drop.attempt()).equals(key)) {
                dropped.remove(at);
            }
        }
    }

    /** Replaces an order's pin, or removes it when the target is empty, in {@link PlanLayout#PIN_ORDER}. */
    private void storePin(OrderId order, Optional<PinTarget> target) {
        List<Pin> current = pins();
        for (int at = current.size() - 1; at >= 0; at--) {
            if (current.get(at).order().equals(order)) {
                pins.remove(at);
            }
        }
        if (target.isPresent()) {
            Pin pin = new Pin(order, target.get());
            List<Pin> left = pins();
            int at = 0;
            while (at < left.size() && PlanLayout.PIN_ORDER.compare(left.get(at), pin) <= 0) {
                at++;
            }
            pins.add(at, new StoredPin(pin));
        }
    }

    private void storeViolations(List<Violation> next) {
        violations.clear();
        next.stream().map(StoredViolation::new).forEach(violations::add);
    }

    /** Journals an edit, forgetting the oldest past {@link #JOURNAL_LIMIT}; a new edit clears redo. */
    private void journal(Edit edit) {
        undo.add(new StoredEdit(edit));
        while (undo.size() > JOURNAL_LIMIT) {
            undo.remove(0);
        }
        redo.clear();
    }

    private void clearJournal() {
        undo.clear();
        redo.clear();
    }

    /**
     * Lays the routes a solve or an acceptance decided on onto the plan's own, in their order: a
     * route or a stop whose ID the plan holds keeps its instance, so a stop that changes routes is
     * the same entity on its new route's list, and the rest are new. A route the decision leaves out
     * leaves the plan, and so does every stop no laid route holds.
     */
    private void layRoutes(List<PlacedRoute> next) {
        Map<RouteId, Route> held = new HashMap<>();
        Map<StopId, PlannedStop> heldStops = new HashMap<>();
        for (Route route : routes) {
            held.put(route.id(), route);
            route.stops().forEach(stop -> heldStops.put(stop.id(), stop));
        }
        List<Route> laid = new ArrayList<>();
        for (PlacedRoute placed : next) {
            Route route = held.remove(placed.id());
            if (route == null) {
                route = new Route(placed.id(), placed.index(), placed.vehicle(), placed.driver());
            }
            route.reindexAs(placed.index());
            route.driveBy(placed.driver());
            route.lockAs(placed.lock());
            route.checkAs(placed.check());
            route.withdrawAs(placed.withdrawal());
            route.confirmReturnAt(placed.confirmedReturn());
            route.timeAs(placed.plan());
            route.holdStops(placed.stops().stream()
                    .map(stop -> {
                        PlannedStop kept = heldStops.get(stop.id());
                        if (kept == null) {
                            return new PlannedStop(stop.id(), stop.order(), stop.attempt(), stop.plan(), stop.late());
                        }
                        kept.timeAs(stop.plan());
                        kept.markLate(stop.late());
                        return kept;
                    })
                    .toList());
            laid.add(route);
        }
        Set<PlannedStop> onLaidRoutes = new HashSet<>();
        laid.forEach(route -> onLaidRoutes.addAll(route.stops()));
        stops.removeIf(stop -> !onLaidRoutes.contains(stop));
        stops.addAll(onLaidRoutes);
        routes.clear();
        routes.addAll(laid);
    }

    // ---- Checks --------------------------------------------------------------------------------

    private void requireReady() {
        if (status != StatusKind.READY) {
            throw new RuleViolation(new PlanNotReady(id()));
        }
    }

    private void requireDraftOrReady() {
        if (!(status == StatusKind.DRAFT || status == StatusKind.READY)) {
            throw new RuleViolation(new PlanNotDraftOrReady(id()));
        }
    }

    private Status.Published requirePublished() {
        if (!(status() instanceof Status.Published published)) {
            throw new RuleViolation(new PlanNotPublished(id()));
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

    /**
     * Checks that an evaluation describes a layout: its routes are exactly these routes, each with
     * its vehicle, its driver and these stops in this order, and its violations name only these
     * routes (ADR-101).
     *
     * @throws RuleViolation with {@link StaleEvaluation} when it describes another layout
     */
    private void requireDescribes(Evaluation evaluation, PlanLayout laidOut) {
        Map<RouteId, RouteTimes> times = timesOf(evaluation);
        List<LaidOutRoute> described = laidOut.routes();
        boolean sameRoutes = times.size() == described.size()
                && evaluation.routes().size() == described.size()
                && described.stream().allMatch(route -> describes(times.get(route.route()), route));
        boolean violationsOnTheseRoutes = evaluation.violations().stream()
                .allMatch(violation -> violation.route().map(times::containsKey).orElse(true));
        if (!sameRoutes || !violationsOnTheseRoutes) {
            throw new RuleViolation(new StaleEvaluation(evaluation.planVersion(), version()));
        }
    }

    /** Whether an evaluation's route is this route: its vehicle, its driver, and its stops in order. */
    private static boolean describes(@Nullable RouteTimes times, LaidOutRoute route) {
        return times != null
                && times.vehicle().equals(route.vehicle())
                && times.driver().equals(route.driver())
                && times.stops().stream()
                        .map(StopTiming::stop)
                        .toList()
                        .equals(route.stops().stream().map(LaidOutStop::stop).toList());
    }

    /**
     * Marks every route checked by an evaluation that {@link #requireDescribes describes} the plan as
     * it is. With {@code applyTimes}, the routes and stops also take its times (FR-23, BR-68), and a
     * route or stop it couldn't time keeps none (ADR-112).
     */
    private void judge(Evaluation evaluation, boolean applyTimes) {
        Map<RouteId, RouteTimes> times = timesOf(evaluation);
        for (Route route : routes) {
            route.checkAs(CheckState.CHECKED);
            if (applyTimes) {
                RouteTimes timed = Objects.requireNonNull(times.get(route.id()), "the evaluation describes the route");
                route.timeAs(timed.plan());
                List<PlannedStop> stops = route.stops();
                for (int position = 0; position < stops.size(); position++) {
                    stops.get(position).timeAs(timed.stops().get(position).plan());
                }
            }
        }
    }

    private static Map<RouteId, RouteTimes> timesOf(Evaluation evaluation) {
        Map<RouteId, RouteTimes> times = new HashMap<>();
        evaluation.routes().forEach(route -> times.put(route.route(), route));
        return times;
    }

    private Route route(RouteId route) {
        return findRoute(route).orElseThrow(() -> new RuleViolation(new RouteNotInPlan(route)));
    }

    private Optional<Route> findRoute(RouteId route) {
        return routes.stream().filter(candidate -> candidate.id().equals(route)).findFirst();
    }

    /** A route a command already found, while it changes the plan. */
    private Route held(RouteId route) {
        return findRoute(route).orElseThrow(() -> new AssertionError("a plan command changes only routes it holds"));
    }

    /** Where a stop is: its route, its position from 0, and the stop. */
    private record Located(Route route, int position, PlannedStop stop) {}

    private Located locateOrRefuse(StopId stop) {
        return locate(stop).orElseThrow(() -> new RuleViolation(new StopNotInPlan(stop)));
    }

    private Optional<Located> locate(StopId stop) {
        return locate(candidate -> candidate.id().equals(stop));
    }

    private Optional<Located> locate(StopKey key) {
        return locate(candidate -> candidate.key().equals(key));
    }

    private Optional<Located> locate(Predicate<PlannedStop> matches) {
        for (Route route : routes) {
            List<PlannedStop> stops = route.stops();
            for (int position = 0; position < stops.size(); position++) {
                if (matches.test(stops.get(position))) {
                    return Optional.of(new Located(route, position, stops.get(position)));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<DroppedOrder> drop(StopKey key) {
        return dropped().stream()
                .filter(drop -> new StopKey(drop.order(), drop.attempt()).equals(key))
                .findFirst();
    }

    private Optional<PinTarget> pinOf(OrderId order) {
        return pins().stream()
                .filter(pin -> pin.order().equals(order))
                .map(Pin::target)
                .findFirst();
    }

    /** Every order and attempt the plan serves or drops, routes first, in order. */
    private List<StopKey> keys() {
        List<StopKey> keys = new ArrayList<>();
        routes.forEach(route -> keys.addAll(route.keys()));
        dropped().forEach(drop -> keys.add(new StopKey(drop.order(), drop.attempt())));
        return keys;
    }

    private List<RouteId> routeIds() {
        return routes.stream().map(Route::id).toList();
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

    private static void requireOneRoutePerVehicleAndDriver(List<PlacedRoute> routes) {
        Map<VehicleId, RouteId> vehicles = new HashMap<>();
        Map<AccountId, RouteId> drivers = new HashMap<>();
        for (PlacedRoute route : routes) {
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

    /**
     * Makes an edit with its evaluation: the evaluation must describe the layout the edit leaves,
     * whose times the plan then takes, with its violations, and the edit is journaled.
     */
    private void commitEdit(Edit edit, Evaluation evaluation) {
        PlanLayout after = viewAfter(edit, FORWARD);
        requireDescribes(evaluation, after);
        forgetPendingRevision();
        change(edit, FORWARD, after);
        judge(evaluation, true);
        storeViolations(evaluation.violations());
        journal(edit);
        requireInvariants();
    }

    /** As {@link #commitEdit}, except that a draft keeps no violations and journals nothing. */
    private void commitPinEdit(Edit edit, Evaluation evaluation) {
        PlanLayout after = viewAfter(edit, FORWARD);
        requireDescribes(evaluation, after);
        forgetPendingRevision();
        change(edit, FORWARD, after);
        judge(evaluation, true);
        if (status == StatusKind.READY) {
            storeViolations(evaluation.violations());
            journal(edit);
        }
        requireInvariants();
    }

    /** Makes a lock's edit, which changes no route's stops and so needs no evaluation, and journals it. */
    private void commitLockEdit(Edit edit) {
        change(edit, FORWARD, layout());
        journal(edit);
        requireInvariants();
    }

    /** {@link #moveStop}'s edit, which {@link #afterMove} also makes, so the two can't differ. */
    private Edit.MovedStop moving(StopId stop, RouteId toRoute, int position) {
        Located from = locateOrRefuse(stop);
        Route target = route(toRoute);
        int last = from.route().id().equals(toRoute)
                ? target.stops().size() - 1
                : target.stops().size();
        requirePosition(toRoute, position, last);
        requireUnlocked(from.route());
        requireUnlocked(target);
        return new Edit.MovedStop(stop, from.route().id(), from.position(), toRoute, position);
    }

    /** {@link #takeOffRoute}'s edit, which {@link #afterTakeOff} also makes. */
    private Edit.TookOff takingOff(StopId stop) {
        Located at = locateOrRefuse(stop);
        requireUnlocked(at.route());
        PlannedStop taken = at.stop();
        return new Edit.TookOff(stop, taken.order(), taken.attempt(), at.route().id(), at.position(), taken.plan());
    }

    /** {@link #placeDropped}'s edit, which {@link #afterPlacing} also makes. */
    private Edit.Placed placing(OrderId order, Attempt attempt, RouteId toRoute, int position, StopId newStop) {
        StopKey key = new StopKey(order, attempt);
        DroppedOrder drop = drop(key).orElseThrow(() -> new RuleViolation(new OrderNotDropped(order, attempt)));
        if (drop.reason().isAboutLocation()) {
            throw new RuleViolation(new OrderNotRoutable(order));
        }
        Route target = route(toRoute);
        requirePosition(toRoute, position, target.stops().size());
        requireUnlocked(target);
        return new Edit.Placed(order, attempt, newStop, toRoute, position, drop.reason());
    }

    /** {@link #pin}'s edit, which {@link #afterPin} also makes. */
    private Edit.Pinned pinning(OrderId order, PinTarget target) {
        return new Edit.Pinned(order, pinOf(order), target);
    }

    /** {@link #unpin}'s edit, which {@link #afterUnpin} also makes. */
    private Edit.Unpinned unpinning(OrderId order) {
        PinTarget before = pinOf(order).orElseThrow(() -> new RuleViolation(new OrderNotPinned(order)));
        return new Edit.Unpinned(order, before);
    }

    /** {@link #undo}'s edit, which {@link #afterUndo} also undoes. */
    private Edit lastUndo() {
        if (undo.isEmpty()) {
            throw new RuleViolation(new NothingToUndo());
        }
        return undo.get(undo.size() - 1).edit();
    }

    /** {@link #redo}'s edit, which {@link #afterRedo} also makes again. */
    private Edit lastRedo() {
        if (redo.isEmpty()) {
            throw new RuleViolation(new NothingToRedo());
        }
        return redo.get(redo.size() - 1).edit();
    }

    /**
     * {@link #handOver}'s layout before it is judged, which {@link #afterHandOver} also returns.
     *
     * @throws RuleViolation with {@link RouteNotInPlan}, {@link RouteWithdrawn} or {@link
     *     DriverAlreadyHasRoute}
     */
    private PlanLayout handingOver(RouteId route, AccountId driver) {
        if (route(route).withdrawn()) {
            throw new RuleViolation(new RouteWithdrawn(route));
        }
        Optional<Route> driving = routes.stream()
                .filter(candidate ->
                        !candidate.withdrawn() && candidate.driver().equals(driver))
                .findFirst();
        if (driving.isPresent()) {
            throw new RuleViolation(
                    new DriverAlreadyHasRoute(driver, driving.get().id()));
        }
        return layout().drivenBy(route, driver);
    }

    /**
     * The layout an edit leaves, made or undone, without changing the plan: what the edit's query
     * returns and its evaluation must describe. {@link #change} makes the same edit in place.
     *
     * @throws RuleViolation with {@link StopNotInPlan} when the edit moves or takes off a stop the
     *     plan doesn't hold
     */
    private PlanLayout viewAfter(Edit edit, boolean forward) {
        PlanLayout now = layout();
        return switch (edit) {
            case Edit.MovedStop moved -> {
                LaidOutStop stop = laidOut(locateOrRefuse(moved.stop()).stop());
                yield forward
                        ? now.removing(moved.stop()).inserting(moved.toRoute(), moved.toPosition(), stop)
                        : now.removing(moved.stop()).inserting(moved.fromRoute(), moved.fromPosition(), stop);
            }
            case Edit.TookOff took -> {
                if (forward) {
                    locateOrRefuse(took.stop());
                    yield now.removing(took.stop()).adding(removedByDispatcher(took.order(), took.attempt()));
                }
                yield now.without(took.order(), took.attempt())
                        .inserting(
                                took.route(),
                                took.position(),
                                new LaidOutStop(took.stop(), took.order(), took.attempt()));
            }
            case Edit.Placed placed ->
                forward
                        ? now.without(placed.order(), placed.attempt())
                                .inserting(
                                        placed.route(),
                                        placed.position(),
                                        new LaidOutStop(placed.stop(), placed.order(), placed.attempt()))
                        : now.removing(placed.stop())
                                .adding(new DroppedOrder(placed.order(), placed.attempt(), placed.before()));
            case Edit.Pinned pinned ->
                now.pinning(pinned.order(), forward ? Optional.of(pinned.after()) : pinned.before());
            case Edit.Unpinned unpinned ->
                now.pinning(unpinned.order(), forward ? Optional.empty() : Optional.of(unpinned.before()));
            case Edit.Locked locked -> now;
            case Edit.Unlocked unlocked -> now;
        };
    }

    /**
     * Makes an edit, or undoes it, in place: a moved stop is the same entity on its new route, a
     * stop taken off leaves the plan, and a stop put back or placed is a new entity under the edit's
     * stop ID. Every rule was checked before, so it ends by checking that it left exactly the layout
     * {@link #viewAfter} returned, which its query returned and its evaluation described.
     */
    private void change(Edit edit, boolean forward, PlanLayout expected) {
        switch (edit) {
            case Edit.MovedStop moved -> {
                if (forward) {
                    moveTo(moved.stop(), moved.toRoute(), moved.toPosition());
                } else {
                    moveTo(moved.stop(), moved.fromRoute(), moved.fromPosition());
                }
            }
            case Edit.TookOff took -> {
                StopKey key = new StopKey(took.order(), took.attempt());
                if (forward) {
                    takeOff(heldStop(took.stop()));
                    addDrop(removedByDispatcher(took.order(), took.attempt()));
                } else {
                    removeDrop(key);
                    putOn(
                            held(took.route()),
                            took.position(),
                            new PlannedStop(took.stop(), took.order(), took.attempt(), took.plan(), Optional.empty()));
                }
            }
            case Edit.Placed placed -> {
                StopKey key = new StopKey(placed.order(), placed.attempt());
                if (forward) {
                    removeDrop(key);
                    putOn(
                            held(placed.route()),
                            placed.position(),
                            new PlannedStop(
                                    placed.stop(),
                                    placed.order(),
                                    placed.attempt(),
                                    Optional.empty(),
                                    Optional.empty()));
                } else {
                    takeOff(heldStop(placed.stop()));
                    addDrop(new DroppedOrder(placed.order(), placed.attempt(), placed.before()));
                }
            }
            case Edit.Pinned pinned ->
                storePin(pinned.order(), forward ? Optional.of(pinned.after()) : pinned.before());
            case Edit.Unpinned unpinned ->
                storePin(unpinned.order(), forward ? Optional.empty() : Optional.of(unpinned.before()));
            case Edit.Locked locked ->
                held(locked.route()).lockAs(forward ? Optional.of(locked.after()) : locked.before());
            case Edit.Unlocked unlocked ->
                held(unlocked.route()).lockAs(forward ? Optional.empty() : Optional.of(unlocked.before()));
        }
        if (!layout().equals(expected)) {
            throw new AssertionError("a plan edit left another layout than its query returned");
        }
    }

    /** Moves a stop off its route, then into a route at a position counted in the route without it. */
    private void moveTo(StopId stop, RouteId to, int position) {
        Located at = heldStop(stop);
        at.route().remove(at.stop());
        held(to).insert(position, at.stop());
    }

    /** Puts a stop that joins the plan on a route, at a position from 0. */
    private void putOn(Route route, int position, PlannedStop stop) {
        route.insert(position, stop);
        stops.add(stop);
    }

    /** Puts a stop that joins the plan at the end of a route. */
    private void appendTo(Route route, PlannedStop stop) {
        route.append(stop);
        stops.add(stop);
    }

    /** Takes a stop off its route and off the plan, whose save then deletes it. */
    private void takeOff(Located at) {
        at.route().remove(at.stop());
        stops.remove(at.stop());
    }

    /** A stop a command already found, while it changes the plan. */
    private Located heldStop(StopId stop) {
        return locate(stop).orElseThrow(() -> new AssertionError("a plan command changes only stops it holds"));
    }

    private static DroppedOrder removedByDispatcher(OrderId order, Attempt attempt) {
        return new DroppedOrder(order, attempt, new DropReason.RemovedByDispatcher());
    }

    private static LaidOutStop laidOut(PlannedStop stop) {
        return new LaidOutStop(stop.id(), stop.order(), stop.attempt());
    }

    private static PlannedStop lateStop(LateFact fact, LateOutcome late) {
        return new PlannedStop(fact.stop(), fact.order(), fact.attempt(), Optional.empty(), Optional.of(late));
    }

    // ---- Revisions -----------------------------------------------------------------------------

    /**
     * Ends a change to a published or completed plan, already made in place, as its next revision,
     * and raises {@link PlanRevised}.
     */
    private void revise(
            RevisionCause cause,
            Optional<ProposalId> proposal,
            Optional<AccountId> by,
            Instant now,
            List<RouteId> changed,
            List<VehicleWithdrawal> withdrawn,
            List<OrderId> added) {
        RevisionNo number;
        if (status() instanceof Status.Published published) {
            number = published.revision().next();
            storeStatus(new Status.Published(number));
        } else if (status() instanceof Status.Completed completed) {
            number = completed.revision().next();
            storeStatus(new Status.Completed(number));
        } else {
            throw new AssertionError("only a published or completed plan is revised");
        }
        requireInvariants();
        pendingRevision = revisionOf(number, cause, proposal, by, now);
        int revisionNumber = number.value();
        PlanId plan = id();
        List<VehicleId> vehicles =
                withdrawn.stream().map(VehicleWithdrawal::vehicle).toList();
        List<WithdrawalCause> causes =
                withdrawn.stream().map(VehicleWithdrawal::cause).toList();
        raise(
                PlanRevised.class,
                id,
                eventId -> new PlanRevised(
                        eventId, now, plan, date, revisionNumber, cause, changed, vehicles, causes, added));
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
                routes.stream().map(Route::revision).toList());
    }

    // ---- Placing solved routes -----------------------------------------------------------------

    /**
     * A stop as a solve or an acceptance places it, before the plan's routes take it: a value, so
     * that every rule of the command is checked before any field changes.
     */
    private record PlacedStop(
            StopId id, OrderId order, Attempt attempt, Optional<StopPlan> plan, Optional<LateOutcome> late) {

        static PlacedStop of(PlannedStop stop) {
            return new PlacedStop(stop.id(), stop.order(), stop.attempt(), stop.plan(), stop.late());
        }

        StopKey key() {
            return new StopKey(order, attempt);
        }
    }

    /**
     * A route as a solve or an acceptance places it, before the plan's routes take it: a value, so
     * that every rule of the command is checked before any field changes. {@link #layRoutes} then
     * lays the routes so decided onto the plan's own.
     */
    private record PlacedRoute(
            RouteId id,
            RouteIndex index,
            VehicleId vehicle,
            AccountId driver,
            Optional<RouteLock> lock,
            CheckState check,
            Optional<Withdrawal> withdrawal,
            Optional<SolveTime> confirmedReturn,
            Optional<RoutePlan> plan,
            List<PlacedStop> stops) {

        PlacedRoute {
            stops = List.copyOf(stops);
        }

        static PlacedRoute of(Route route) {
            return new PlacedRoute(
                    route.id(),
                    route.index(),
                    route.vehicle(),
                    route.driver(),
                    route.lock(),
                    route.check(),
                    route.withdrawal(),
                    route.confirmedReturn(),
                    route.plan(),
                    route.stops().stream().map(PlacedStop::of).toList());
        }

        boolean withdrawn() {
            return withdrawal.isPresent();
        }

        List<StopId> stopIds() {
            return stops.stream().map(PlacedStop::id).toList();
        }

        List<StopKey> keys() {
            return stops.stream().map(PlacedStop::key).toList();
        }

        PlacedRoute withStops(List<PlacedStop> newStops) {
            return new PlacedRoute(
                    id, index, vehicle, driver, lock, check, withdrawal, confirmedReturn, plan, newStops);
        }

        PlacedRoute withLock(Optional<RouteLock> newLock) {
            return new PlacedRoute(
                    id, index, vehicle, driver, newLock, check, withdrawal, confirmedReturn, plan, stops);
        }

        PlacedRoute withCheck(CheckState newCheck) {
            return new PlacedRoute(
                    id, index, vehicle, driver, lock, newCheck, withdrawal, confirmedReturn, plan, stops);
        }

        PlacedRoute withWithdrawal(Optional<Withdrawal> newWithdrawal) {
            return new PlacedRoute(
                    id, index, vehicle, driver, lock, check, newWithdrawal, confirmedReturn, plan, stops);
        }

        PlacedRoute withIndex(RouteIndex newIndex) {
            return new PlacedRoute(
                    id, newIndex, vehicle, driver, lock, check, withdrawal, confirmedReturn, plan, stops);
        }

        PlacedRoute withPlan(Optional<RoutePlan> newPlan) {
            return new PlacedRoute(
                    id, index, vehicle, driver, lock, check, withdrawal, confirmedReturn, newPlan, stops);
        }
    }

    private List<PlacedRoute> placedRoutes() {
        return routes.stream().map(PlacedRoute::of).toList();
    }

    /**
     * Turns solved routes into the plan's routes (ADR-101):
     *
     * <ul>
     *   <li>a vehicle with a route in {@code current} keeps its ID and index, the stops {@code keptOf}
     *       keeps of that route ahead of the solved ones, and its confirmed return when its driver
     *       stays;
     *   <li>an order and attempt on any route of {@code stopSource} keeps its stop's ID and late mark;
     *   <li>the rest take new IDs and the lowest index that neither {@code keptAlongside} nor the other
     *       routes hold;
     *   <li>no route takes a lock: {@link #carryLocks} gives a daily solve's routes theirs.
     * </ul>
     *
     * One route per vehicle and driver, over these routes and the active routes of {@code
     * keptAlongside}, is checked before any index is handed out, so BR-39's refusals come before
     * {@link RouteIndexesUsedUp}.
     */
    private static List<PlacedRoute> place(
            List<SolvedRoute> solved,
            List<PlacedRoute> current,
            List<PlacedRoute> stopSource,
            List<PlacedRoute> keptAlongside,
            FreshIds ids,
            Function<VehicleId, Optional<Withdrawal>> withdrawalOf,
            Function<PlacedRoute, List<PlacedStop>> keptOf) {
        Map<StopKey, PlacedStop> existingStops = new HashMap<>();
        stopSource.forEach(route -> route.stops().forEach(stop -> existingStops.put(stop.key(), stop)));
        List<PlacedRoute> provisional = new ArrayList<>();
        List<Boolean> needsIndex = new ArrayList<>();
        for (SolvedRoute route : solved) {
            List<PlacedStop> stops = route.stops().stream()
                    .map(stop -> {
                        PlacedStop existing = existingStops.get(new StopKey(stop.order(), stop.attempt()));
                        StopId stopId = existing != null ? existing.id() : StopId.of(ids.next());
                        Optional<LateOutcome> late = existing != null ? existing.late() : Optional.empty();
                        return new PlacedStop(stopId, stop.order(), stop.attempt(), Optional.of(stop.plan()), late);
                    })
                    .toList();
            Optional<PlacedRoute> kept = current.stream()
                    .filter(candidate -> candidate.vehicle().equals(route.vehicle()))
                    .findFirst();
            List<PlacedStop> allStops = new ArrayList<>(kept.map(keptOf).orElse(List.of()));
            allStops.addAll(stops);
            RouteId routeId = kept.map(PlacedRoute::id).orElseGet(() -> RouteId.of(ids.next()));
            Optional<SolveTime> confirmed =
                    kept.filter(old -> old.driver().equals(route.driver())).flatMap(PlacedRoute::confirmedReturn);
            provisional.add(new PlacedRoute(
                    routeId,
                    kept.map(PlacedRoute::index).orElse(RouteIndex.of(RouteIndex.LOWEST)),
                    route.vehicle(),
                    route.driver(),
                    Optional.empty(),
                    CheckState.CHECKED,
                    withdrawalOf.apply(route.vehicle()),
                    confirmed,
                    route.plan(),
                    allStops));
            needsIndex.add(kept.isEmpty());
        }
        Map<VehicleId, RouteId> routed = new HashMap<>();
        for (PlacedRoute route : provisional) {
            RouteId first = routed.putIfAbsent(route.vehicle(), route.id());
            if (first != null) {
                throw new RuleViolation(new VehicleAlreadyHasRoute(route.vehicle(), first));
            }
        }
        List<PlacedRoute> inForce = new ArrayList<>(provisional);
        inForce.addAll(keptAlongside);
        requireOneRoutePerVehicleAndDriver(inForce);
        Set<RouteIndex> taken = new HashSet<>();
        keptAlongside.forEach(route -> taken.add(route.index()));
        for (int i = 0; i < provisional.size(); i++) {
            if (!needsIndex.get(i)) {
                taken.add(provisional.get(i).index());
            }
        }
        List<PlacedRoute> placed = new ArrayList<>();
        for (int i = 0; i < provisional.size(); i++) {
            PlacedRoute route = provisional.get(i);
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

    /** The plan's locked routes that solved routes don't keep, in plan order (FR-147, FR-238). */
    private List<Route> locksBrokenBy(List<SolvedRoute> solved) {
        return routes.stream()
                .filter(route -> route.lock().isPresent() && !keptBy(route, solved))
                .toList();
    }

    /**
     * Whether solved routes keep a locked route as its lock says (FR-147, ADR-101). Every route has
     * the same empty sequence, so an empty sequence is kept only on its own vehicle's route (ADR-112).
     */
    private static boolean keptBy(Route locked, List<SolvedRoute> solved) {
        List<StopKey> keys = locked.keys();
        Predicate<SolvedRoute> sameSequence =
                route -> keysOf(List.of(route), List.of()).equals(keys);
        Predicate<SolvedRoute> sameVehicle = route -> route.vehicle().equals(locked.vehicle());
        RouteLock lock = locked.lock().orElseThrow();
        return switch (lock) {
            case WHOLE_ROUTE ->
                solved.stream()
                        .anyMatch(route -> sameVehicle.test(route)
                                && route.driver().equals(locked.driver())
                                && sameSequence.test(route));
            case STOP_SEQUENCE ->
                solved.stream()
                        .anyMatch(route -> sameSequence.test(route) && (!keys.isEmpty() || sameVehicle.test(route)));
        };
    }

    /**
     * Gives each lock of the current routes to the route that kept it: a whole-route lock, or a
     * sequence lock on an empty route, stays with its vehicle's route, and any other sequence lock
     * goes to the one route that now serves the sequence.
     */
    private static List<PlacedRoute> carryLocks(List<PlacedRoute> current, List<PlacedRoute> placed) {
        List<PlacedRoute> result = new ArrayList<>(
                placed.stream().map(route -> route.withLock(Optional.empty())).toList());
        for (PlacedRoute locked : current) {
            if (locked.lock().isEmpty()) {
                continue;
            }
            RouteLock lock = locked.lock().get();
            for (int position = 0; position < result.size(); position++) {
                PlacedRoute candidate = result.get(position);
                boolean staysWithItsVehicle =
                        lock == RouteLock.WHOLE_ROUTE || locked.keys().isEmpty();
                boolean holds = staysWithItsVehicle
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

    /** Every order and attempt placed routes serve or drops drop, routes first, in order. */
    private static List<StopKey> heldKeys(List<PlacedRoute> routes, List<DroppedOrder> dropped) {
        List<StopKey> keys = new ArrayList<>();
        routes.forEach(route -> keys.addAll(route.keys()));
        dropped.forEach(drop -> keys.add(new StopKey(drop.order(), drop.attempt())));
        return keys;
    }

    // ---- Accepting a proposal ------------------------------------------------------------------

    /**
     * The stops of a route that a proposal neither serves nor drops: the stops already reported done
     * or in progress, which a proposal holds on no route (BR-10, BR-108, ADR-112).
     */
    private static List<PlacedStop> keptStops(PlacedRoute route, Set<StopKey> taken) {
        return route.stops().stream()
                .filter(stop -> !taken.contains(stop.key()))
                .toList();
    }

    /**
     * The routes an acceptance leaves, in the plan's order: each route as placed or kept, or as it
     * was, and then the new routes in the proposal's order.
     */
    private static List<PlacedRoute> inPlanOrder(
            List<PlacedRoute> before, List<PlacedRoute> placed, List<PlacedRoute> leftOut) {
        Map<RouteId, PlacedRoute> replacing = new LinkedHashMap<>();
        placed.forEach(route -> replacing.put(route.id(), route));
        leftOut.forEach(route -> replacing.put(route.id(), route));
        List<PlacedRoute> next = new ArrayList<>();
        for (PlacedRoute route : before) {
            PlacedRoute replaced = replacing.remove(route.id());
            next.add(replaced != null ? replaced : route);
        }
        next.addAll(replacing.values());
        return next;
    }

    /**
     * A route the proposal gives no route: it keeps the stops the proposal neither serves nor drops.
     * When that changes its stops it is not yet checked, for the re-check job (FR-395), and a route
     * left with no stops has no times, as the validator gives an empty route none (ADR-112).
     */
    private static PlacedRoute leftOutRoute(PlacedRoute route, Optional<Withdrawal> withdrawal, List<PlacedStop> kept) {
        PlacedRoute left = route.withWithdrawal(withdrawal);
        if (kept.equals(route.stops())) {
            return left;
        }
        PlacedRoute changed = left.withStops(kept).withCheck(CheckState.NOT_YET_CHECKED);
        return kept.isEmpty() ? changed.withPlan(Optional.empty()) : changed;
    }

    /**
     * The plan's drops that the proposal replaces with a later attempt of the same order, served or
     * dropped: a dropped order put back onto its own date, whose old attempt the optimizer no longer
     * knows (ADR-115).
     */
    private Set<StopKey> supersededDrops(Set<StopKey> taken) {
        Set<StopKey> superseded = new HashSet<>();
        for (DroppedOrder dropped : dropped()) {
            StopKey drop = new StopKey(dropped.order(), dropped.attempt());
            boolean later = taken.stream()
                    .anyMatch(key ->
                            key.order().equals(drop.order()) && key.attempt().compareTo(drop.attempt()) > 0);
            if (later && !taken.contains(drop)) {
                superseded.add(drop);
            }
        }
        return superseded;
    }

    /**
     * The drops an acceptance leaves: the proposal's, and each other drop of the plan that the
     * proposal neither serves nor drops, carried forward as it was, such as an order put back onto
     * another date, which a published plan keeps as the day's history (ADR-112). A superseded drop
     * leaves.
     */
    private List<DroppedOrder> droppedAfter(ReoptimizationProposal offer, Set<StopKey> taken, Set<StopKey> superseded) {
        List<DroppedOrder> next = new ArrayList<>(offer.dropped());
        dropped().stream()
                .filter(drop -> {
                    StopKey key = new StopKey(drop.order(), drop.attempt());
                    return !taken.contains(key) && !superseded.contains(key);
                })
                .forEach(next::add);
        return next;
    }

    /**
     * BR-07's other half: every order and attempt the plan held, except a superseded drop, the
     * acceptance still serves or drops. Every stop and drop the proposal doesn't hold is kept before
     * this point, so a loss here is a bug in {@link #accept}, not a refusal.
     */
    private void requireNothingLost(List<StopKey> after, Set<StopKey> superseded) {
        Set<StopKey> held = new HashSet<>(after);
        for (StopKey key : keys()) {
            if (!held.contains(key) && !superseded.contains(key)) {
                throw new AssertionError("accepting a proposal would lose an order and attempt the plan held");
            }
        }
    }

    /**
     * The orders an acceptance puts into the plan that it held under no attempt before, in plan order
     * (FR-39, ADR-112). A later attempt of an order the plan held is not an added order.
     */
    private List<OrderId> addedOrders(List<StopKey> after) {
        Set<OrderId> held = new HashSet<>();
        keys().forEach(key -> held.add(key.order()));
        Set<OrderId> added = new LinkedHashSet<>();
        after.stream()
                .map(StopKey::order)
                .filter(order -> !held.contains(order))
                .forEach(added::add);
        return List.copyOf(added);
    }

    /**
     * The withdrawn routes a proposal routes again: a vehicle the proposal withdraws, with no active
     * route but a route withdrawn earlier, keeps its last such route's ID, index and stops (ADR-101,
     * ADR-112).
     */
    private static List<PlacedRoute> rewithdrawnRoutes(
            ReoptimizationProposal proposal, List<PlacedRoute> before, List<PlacedRoute> covered) {
        List<PlacedRoute> matched = new ArrayList<>();
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

    /** BR-07 over the orders and attempts a change would leave, which a caller's input can still break. */
    private static void requireEachOrderOnce(List<StopKey> after) {
        Set<StopKey> seen = new HashSet<>();
        for (StopKey key : after) {
            if (!seen.add(key)) {
                throw new RuleViolation(new OrderAlreadyInPlan(key.order(), key.attempt()));
            }
        }
    }

    /** The changes since the proposal's basis that it moves, drops or relies on (BR-25, BR-82, BR-83). */
    private List<BasisChange> changesSince(
            ReoptimizationProposal proposal, Set<StopKey> taken, ChangesSinceBasis changes, RevisionNo current) {
        List<BasisChange> found = new ArrayList<>();
        for (int number = proposal.basis().revision().value() + 1; number <= current.value(); number++) {
            found.add(new BasisChange.RevisionMade(RevisionNo.of(number)));
        }
        for (StopId stop : changes.reportedStops()) {
            locate(stop)
                    .filter(at -> movesOrDrops(proposal, taken, at))
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
     * Whether accepting the proposal moves or drops a stop: it stays put only on the same vehicle,
     * after the same stops in the same order (BR-83, ADR-101). The stops before it are those of the
     * route as accepted, its kept stops ahead of the proposal's, so a stop the proposal keeps after a
     * reported one stays put (ADR-112).
     */
    private static boolean movesOrDrops(ReoptimizationProposal proposal, Set<StopKey> taken, Located at) {
        StopKey key = at.stop().key();
        List<StopKey> accepted = new ArrayList<>(keptStops(PlacedRoute.of(at.route()), taken).stream()
                .map(PlacedStop::key)
                .toList());
        if (!accepted.contains(key)) {
            Optional<SolvedRoute> serving = proposal.routes().stream()
                    .filter(route -> keysOf(List.of(route), List.of()).contains(key))
                    .findFirst();
            if (serving.isEmpty() || !serving.get().vehicle().equals(at.route().vehicle())) {
                return true;
            }
            accepted.addAll(keysOf(List.of(serving.get()), List.of()));
        }
        List<StopKey> before = at.route().keys().subList(0, at.position());
        return !accepted.subList(0, accepted.indexOf(key)).equals(before);
    }

    private static List<VehicleId> unavailableVehicles(ReoptimizationProposal proposal, ChangesSinceBasis changes) {
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

    private static Optional<Withdrawal> withdrawalFor(ReoptimizationProposal proposal, VehicleId vehicle) {
        return proposal.withdrawn().stream()
                .filter(withdrawal -> withdrawal.vehicle().equals(vehicle))
                .map(withdrawal -> new Withdrawal(withdrawal.cause()))
                .findFirst();
    }

    /** The routes whose driver, stops or withdrawal differ, or that joined or left, in plan order. */
    private static List<RouteId> changedRoutes(List<PlacedRoute> before, List<PlacedRoute> after) {
        List<RouteId> changed = new ArrayList<>();
        for (PlacedRoute route : after) {
            Optional<PlacedRoute> old = before.stream()
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
        for (PlacedRoute route : before) {
            if (after.stream().noneMatch(candidate -> candidate.id().equals(route.id()))) {
                changed.add(route.id());
            }
        }
        return changed;
    }

    // ---- Publishing checks ---------------------------------------------------------------------

    private List<PlanningError> unstaffed(List<RouteStaffing> staffing) {
        return routes.stream()
                .filter(route -> staffing.stream()
                        .noneMatch(answer -> answer.vehicle().equals(route.vehicle())
                                && answer.driver().equals(route.driver())
                                && answer.assignable()))
                .<PlanningError>map(route -> new RouteNotStaffed(route.id(), route.vehicle()))
                .toList();
    }

    /** Step 2's refusals: every order not accounted for, then every order changed since the solve. */
    private List<PlanningError> unaccounted(List<DateOrder> orders) {
        AppliedSolve solve = applied().orElseThrow(() -> new AssertionError("a ready plan has an applied solve"));
        List<StopKey> keys = keys();
        Set<StopKey> held = new HashSet<>(keys);
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
        for (StopKey key : keys) {
            if (!ofTheDate.contains(key.order())) {
                changed.add(key.order());
            }
        }
        List<PlanningError> refusals = new ArrayList<>(notAccounted);
        changed.forEach(order -> refusals.add(new OrderChangedSinceSolve(order)));
        return refusals;
    }

    private static <T> T stored(@Nullable T part) {
        return Objects.requireNonNull(part, "the plan's status keeps this part");
    }
}
