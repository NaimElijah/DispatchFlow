package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.PlanningError.CapacityForAnotherLane;
import com.dispatchflow.planning.domain.PlanningError.JobAlreadyEnded;
import com.dispatchflow.planning.domain.PlanningError.JobNotBuildingMatrix;
import com.dispatchflow.planning.domain.PlanningError.JobNotQueued;
import com.dispatchflow.planning.domain.PlanningError.JobNotRunning;
import com.dispatchflow.planning.domain.PlanningError.ReoptimizationAlreadyRunning;
import com.dispatchflow.planning.domain.PlanningError.ResultForAnotherLane;
import com.dispatchflow.planning.domain.PlanningError.SolveQueueFull;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.basis.ProposalBasis;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.planning.domain.solution.ReoptimizationResult;
import com.dispatchflow.planning.domain.solution.SolveFailure;
import com.dispatchflow.planning.domain.solution.SolveResult;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.SolveFailed;
import com.dispatchflow.planning.events.SolveFailureReason;
import com.dispatchflow.planning.events.SolveSucceeded;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RuleViolation;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One queued or running call to the optimizer for a plan, in one of two lanes (DL §4.4, §9.2, §17).
 * It moves queued → building the matrix → running → succeeded, and fails from any of the first three;
 * once ended it never changes (DL §11). Its worker, the watchdog, startup recovery and the midnight
 * job all end it through the same methods, so the plan's reaction is written once (DL §7.1).
 *
 * <p>Each phase has a deadline that {@link #expireIfOverdue} enforces: 5 minutes in the queue
 * (BR-112), 2 minutes to build the matrix (ADR-101), and the limit plus 30 seconds of search (BR-32,
 * BR-84). A daily job keeps its validated result for its plan to apply; a re-optimization creates a
 * {@link ReoptimizationProposal}.
 *
 * <p>A command's {@code now} before the job's latest recorded time, as a clock stepped back would
 * give, counts as that time, so the job always loads again (ADR-112).
 *
 * <p>It is mapped for JPA (ADR-126, ADR-127): its key is its {@code JobId}'s UUID; its phase is a kind
 * beside the times each phase keeps, with a failure's reason and its violations in a table of their
 * own; and a daily job's result is four tables, its routes, their stops flattened by both positions,
 * its drops and its excluded vehicles, which only a succeeded daily job fills. Every command changes
 * these in place and ends by checking the invariants at rest.
 */
@Entity
@Table(schema = "planning", name = "solve_job")
public class SolveJob extends AggregateRoot {

    /** The longest a job waits in its queue (BR-112). */
    public static final Duration QUEUE_WAIT = Duration.ofMinutes(5);

    /** The longest a job may take to build its matrix; a starting value (ADR-101). */
    public static final Duration MATRIX_DEADLINE = Duration.ofMinutes(2);

    /** How long a search may run past its limit before the job has failed (BR-32, BR-84, ADR-051). */
    public static final Duration OVERRUN_GRACE = Duration.ofSeconds(30);

    /** Where a job is in its lifecycle (DL §9.2). */
    public sealed interface Phase {

        /** Waiting in its lane's queue. */
        record Queued() implements Phase {}

        /**
         * Claimed by its lane's worker, which is building the travel-time matrix; this counts toward
         * the response time but not the search limit (NFR-07, BR-75).
         *
         * @param claimedAt when the worker claimed it
         */
        record BuildingMatrix(Instant claimedAt) implements Phase {

            /** Checks the time is present. */
            public BuildingMatrix {
                Objects.requireNonNull(claimedAt, "claimedAt");
            }
        }

        /**
         * The search is running.
         *
         * @param claimedAt when the worker claimed it
         * @param searchStartedAt when the search started, from which the limit runs (BR-75)
         */
        record Running(Instant claimedAt, Instant searchStartedAt) implements Phase {

            /** Checks both times are present. */
            public Running {
                Objects.requireNonNull(claimedAt, "claimedAt");
                Objects.requireNonNull(searchStartedAt, "searchStartedAt");
            }
        }

        /**
         * It ended with a validated result.
         *
         * @param claimedAt when the worker claimed it
         * @param searchStartedAt when the search started
         * @param endedAt when it ended
         */
        record Succeeded(Instant claimedAt, Instant searchStartedAt, Instant endedAt) implements Phase {

            /** Checks every time is present. */
            public Succeeded {
                Objects.requireNonNull(claimedAt, "claimedAt");
                Objects.requireNonNull(searchStartedAt, "searchStartedAt");
                Objects.requireNonNull(endedAt, "endedAt");
            }
        }

        /**
         * It ended without a result.
         *
         * @param failure why
         * @param endedAt when it ended
         */
        record Failed(SolveFailure failure, Instant endedAt) implements Phase {

            /** Checks both parts are present. */
            public Failed {
                Objects.requireNonNull(failure, "failure");
                Objects.requireNonNull(endedAt, "endedAt");
            }
        }
    }

    private static final String BASIS_ONLY_FOR_REOPTIMIZATION =
            "a basis revision belongs to a re-optimization, and only to one";
    private static final String REOPTIMIZATION_LIMIT = "a re-optimization's limit is 20 seconds";
    private static final String RESULT_ONLY_WHEN_DAILY_SUCCEEDED =
            "a result belongs to a succeeded daily job, and only to one";
    private static final String TIMES_IN_ORDER = "the phase's times are out of order";
    private static final String VIOLATIONS_ONLY_WHEN_FAILED = "violations belong to a failed job";

    /** Which {@link Phase} a job is in. */
    private enum PhaseKind {
        QUEUED,
        BUILDING_MATRIX,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "plan_id")
    private PlanId plan;

    @Enumerated(EnumType.STRING)
    private Lane lane;

    private SolverName solver;

    // PostgreSQL reserves "limit".
    @Column(name = "solve_limit")
    private SolveLimit limit;

    private Seed seed;

    private @Nullable RevisionNo basisRevision;

    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    private PhaseKind phase;

    private @Nullable Instant claimedAt;

    private @Nullable Instant searchStartedAt;

    private @Nullable Instant endedAt;

    @Enumerated(EnumType.STRING)
    private @Nullable SolveFailureReason failureReason;

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "solve_job_violation",
            joinColumns = @JoinColumn(name = "solve_job_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredViolation> failureViolations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "solve_job_route", joinColumns = @JoinColumn(name = "solve_job_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredSolvedRoute> resultRoutes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "solve_job_stop", joinColumns = @JoinColumn(name = "solve_job_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredSolvedStop> resultStops = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "planning", name = "solve_job_drop", joinColumns = @JoinColumn(name = "solve_job_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredDroppedOrder> resultDropped = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "solve_job_excluded_vehicle",
            joinColumns = @JoinColumn(name = "solve_job_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<ExcludedVehicle> resultExcluded = new ArrayList<>();

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected SolveJob() {}

    private SolveJob(
            JobId id,
            PlanId plan,
            Lane lane,
            SolverName solver,
            SolveLimit limit,
            Seed seed,
            @Nullable RevisionNo basisRevision,
            Instant requestedAt) {
        super();
        this.id = id.value();
        this.plan = plan;
        this.lane = lane;
        this.solver = solver;
        this.limit = limit;
        this.seed = seed;
        this.basisRevision = basisRevision;
        this.requestedAt = requestedAt;
        this.phase = PhaseKind.QUEUED;
    }

    /** Builds the job {@link #restore} returns; it goes with {@code restore} (ADR-126). */
    private SolveJob(SolveJobSnapshot stored) {
        super(stored.version());
        this.id = stored.id().value();
        this.plan = stored.plan();
        this.lane = stored.lane();
        this.solver = stored.solver();
        this.limit = stored.limit();
        this.seed = stored.seed();
        this.basisRevision = stored.basisRevision().orElse(null);
        this.requestedAt = stored.requestedAt();
        storePhase(stored.phase());
        stored.result().ifPresent(this::storeResult);
    }

    /**
     * Queues a job. {@link Plan} calls it, so a daily job exists only with its plan solving, and a
     * re-optimization only for a published plan (FR-35).
     *
     * @param id the new job's ID
     * @param plan the plan it solves
     * @param lane its lane
     * @param solver the solver it runs (FR-20)
     * @param limit its search limit
     * @param seed its seed (NFR-13)
     * @param basisRevision the revision a re-optimization starts from; empty for a daily solve
     * @param capacity the lane's queue, read under its lock
     * @param now the time of the request
     * @return the queued job
     * @throws RuleViolation with {@link CapacityForAnotherLane} when the capacity was read for another
     *     lane, {@link ReoptimizationAlreadyRunning} when the plan already has a queued or started
     *     re-optimization (BR-38), or {@link SolveQueueFull} when the lane holds 5 queued jobs
     *     (BR-112)
     */
    static SolveJob queue(
            JobId id,
            PlanId plan,
            Lane lane,
            SolverName solver,
            SolveLimit limit,
            Seed seed,
            Optional<RevisionNo> basisRevision,
            LaneCapacity capacity,
            Instant now) {
        if (capacity.lane() != lane) {
            throw new RuleViolation(new CapacityForAnotherLane(lane, capacity.lane()));
        }
        if (lane == Lane.REOPTIMIZATION && capacity.holdsAJobOf(plan)) {
            throw new RuleViolation(new ReoptimizationAlreadyRunning(plan));
        }
        if (capacity.isFull()) {
            throw new RuleViolation(new SolveQueueFull(lane, LaneCapacity.QUEUE_LIMIT));
        }
        return new SolveJob(id, plan, lane, solver, limit, seed, basisRevision.orElse(null), now);
    }

    /**
     * Rebuilds a stored job; only the persistence adapter calls it.
     *
     * @param snapshot the stored state
     * @return the job
     * @throws StoredStateInvalid when the state breaks an invariant that holds at rest: the phase's
     *     times out of order, a basis revision outside the re-optimization lane, a re-optimization
     *     limit other than 20 seconds, or a result anywhere but a succeeded daily job
     */
    public static SolveJob restore(SolveJobSnapshot snapshot) {
        boolean reoptimization = snapshot.lane() == Lane.REOPTIMIZATION;
        if (reoptimization != snapshot.basisRevision().isPresent()) {
            throw invalid(BASIS_ONLY_FOR_REOPTIMIZATION);
        }
        if (reoptimization && !snapshot.limit().equals(SolveLimit.REOPTIMIZATION)) {
            throw invalid(REOPTIMIZATION_LIMIT);
        }
        boolean keepsResult = snapshot.lane() == Lane.DAILY && snapshot.phase() instanceof Phase.Succeeded;
        if (keepsResult != snapshot.result().isPresent()) {
            throw invalid(RESULT_ONLY_WHEN_DAILY_SUCCEEDED);
        }
        if (!timesInOrder(snapshot.requestedAt(), snapshot.phase())) {
            throw invalid(TIMES_IN_ORDER);
        }
        return new SolveJob(snapshot);
    }

    /**
     * Checks the job as Hibernate loaded it (ADR-126 decision 8): the checks {@link #restore} runs,
     * after rebuilding its phase and its result, and that only a failed job stores violations, which
     * a snapshot could not break but stored rows can.
     *
     * @throws StoredStateInvalid when the stored state breaks one of them, or a stored value is
     *     refused
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw invalid(broken);
        });
    }

    /**
     * Its lane's worker claims it and starts building the matrix (DL §9.2).
     *
     * @param now the time of the claim
     * @throws RuleViolation with {@link JobAlreadyEnded} once ended, or {@link JobNotQueued} when it
     *     is not queued
     */
    public void claim(Instant now) {
        requireNotEnded();
        if (phase != PhaseKind.QUEUED) {
            throw new RuleViolation(new JobNotQueued(id()));
        }
        storePhase(new Phase.BuildingMatrix(atOrAfterLatest(now)));
        requireInvariants();
    }

    /**
     * The matrix is ready and the search starts; the limit runs from now (BR-75).
     *
     * @param now when the search started
     * @throws RuleViolation with {@link JobAlreadyEnded} once ended, or {@link JobNotBuildingMatrix}
     *     when it is not building its matrix
     */
    public void searchStarted(Instant now) {
        requireNotEnded();
        if (!(phase() instanceof Phase.BuildingMatrix building)) {
            throw new RuleViolation(new JobNotBuildingMatrix(id()));
        }
        storePhase(new Phase.Running(building.claimedAt(), atOrAfterLatest(now)));
        requireInvariants();
    }

    /**
     * A daily solve returned a validated solution, which the job keeps for its plan (BR-15). Raises
     * {@link SolveSucceeded}. It keeps the solution's routes, drops and excluded vehicles (FR-197).
     * Its routes and drops are checked as a proposal's are, so a result its plan could never hold
     * fails the job instead of leaving the plan solving (ADR-112): the worker then fails it with
     * {@code SolveFailure.rejected}, naming the broken rule as the validator does, BR-07 with the order
     * or BR-39 with the vehicle or the driver. The worker asks {@code Plan.locksBrokenBy} first, and
     * fails the job with {@code SolveFailure.lockNotHonoured} when a lock isn't kept (FR-238).
     *
     * @param solution the validated solution
     * @param now when it ended
     * @throws RuleViolation with {@link JobAlreadyEnded} once ended, {@link ResultForAnotherLane} for a
     *     re-optimization, {@link JobNotRunning} when its search is not running, or {@link
     *     PlanningError.OrderAlreadyInPlan} (BR-07), {@link PlanningError.VehicleRoutedTwice} or {@link
     *     PlanningError.DriverRoutedTwice} (BR-39) for a solution no plan could hold
     */
    public void succeed(ValidatedSolution solution, Instant now) {
        requireNotEnded();
        requireLane(Lane.DAILY);
        Phase.Running running = requireRunning();
        Optional<PlanningError> broken = SolutionShape.brokenBy(solution.routes(), solution.dropped(), List.of());
        if (broken.isPresent()) {
            throw new RuleViolation(broken.get());
        }
        Instant ended = atOrAfterLatest(now);
        storeResult(new SolveResult(solution.routes(), solution.dropped(), solution.excluded()));
        endSucceeded(running, ended);
        requireInvariants();
    }

    /**
     * A re-optimization returned a validated solution, which becomes a proposal waiting for a
     * dispatcher (FR-36, FR-255). Raises {@link SolveSucceeded}.
     *
     * @param proposalId the new proposal's ID
     * @param reoptimization the validated solution and the preview's facts
     * @param now when it ended
     * @return the new proposal, created in the same transaction (DL §6.1)
     * @throws RuleViolation with {@link JobAlreadyEnded} once ended, {@link ResultForAnotherLane} for a
     *     daily solve, {@link JobNotRunning} when its search is not running, or {@link
     *     PlanningError.VehicleRoutedTwice} when the solution gives a vehicle two routes
     */
    public ReoptimizationProposal succeed(ProposalId proposalId, ReoptimizationResult reoptimization, Instant now) {
        requireNotEnded();
        requireLane(Lane.REOPTIMIZATION);
        Phase.Running running = requireRunning();
        RevisionNo revision = Objects.requireNonNull(basisRevision, "a re-optimization has a basis revision");
        Instant ended = atOrAfterLatest(now);
        ReoptimizationProposal proposal = ReoptimizationProposal.create(
                proposalId, plan, id(), new ProposalBasis(revision, reoptimization.factsAt()), reoptimization, ended);
        endSucceeded(running, ended);
        requireInvariants();
        return proposal;
    }

    /**
     * The job failed, from its worker, the watchdog, startup recovery or the midnight job (FR-65,
     * BR-74, BR-85, BR-128). Any phase before the end may fail. Raises {@link SolveFailed}.
     *
     * @param failure why
     * @param now when it failed
     * @throws RuleViolation with {@link JobAlreadyEnded} once ended
     */
    public void fail(SolveFailure failure, Instant now) {
        requireNotEnded();
        endFailed(failure, atOrAfterLatest(now));
        requireInvariants();
    }

    /**
     * The watchdog's check: fails the job when it is past its phase's deadline, and otherwise changes
     * nothing (BR-32, BR-84, BR-112, ADR-101). A job is overdue strictly after its deadline.
     *
     * @param now the watchdog's time
     * @throws RuleViolation with {@link JobAlreadyEnded} once ended, so a watchdog that lost a race
     *     stops (DL §11)
     */
    public void expireIfOverdue(Instant now) {
        requireNotEnded();
        Instant deadline;
        SolveFailureReason reason;
        Phase current = phase();
        if (current instanceof Phase.BuildingMatrix building) {
            deadline = building.claimedAt().plus(MATRIX_DEADLINE);
            reason = SolveFailureReason.MATRIX_TOO_LONG;
        } else if (current instanceof Phase.Running running) {
            deadline = running.searchStartedAt().plus(limit.duration()).plus(OVERRUN_GRACE);
            reason = SolveFailureReason.OVERRAN;
        } else {
            deadline = requestedAt.plus(QUEUE_WAIT);
            reason = SolveFailureReason.QUEUED_TOO_LONG;
        }
        if (now.isAfter(deadline)) {
            endFailed(SolveFailure.of(reason), now);
            requireInvariants();
        }
    }

    /**
     * The job's ID.
     *
     * @return the ID
     */
    public JobId id() {
        return JobId.of(id);
    }

    /**
     * The plan it solves.
     *
     * @return the plan's ID
     */
    public PlanId plan() {
        return plan;
    }

    /**
     * The daily result it keeps, for its plan to apply (ADR-101).
     *
     * @return the result once a daily job succeeded; otherwise empty
     */
    public Optional<SolveResult> result() {
        if (!keepsResult()) {
            return Optional.empty();
        }
        return Optional.of(new SolveResult(
                StoredSolvedRoute.assemble(resultRoutes, resultStops),
                resultDropped.stream().map(StoredDroppedOrder::droppedOrder).toList(),
                resultExcluded));
    }

    /**
     * The job's state, for the repository and for tests.
     *
     * @return a snapshot that {@link #restore} rebuilds
     */
    public SolveJobSnapshot snapshot() {
        return new SolveJobSnapshot(
                id(),
                savedVersion().orElse(null),
                plan,
                lane,
                solver,
                limit,
                seed,
                Optional.ofNullable(basisRevision),
                requestedAt,
                phase(),
                result());
    }

    /** The phase its kind and times store, with a failure's reason and violations. */
    private Phase phase() {
        return switch (phase) {
            case QUEUED -> new Phase.Queued();
            case BUILDING_MATRIX -> new Phase.BuildingMatrix(stored(claimedAt));
            case RUNNING -> new Phase.Running(stored(claimedAt), stored(searchStartedAt));
            case SUCCEEDED -> new Phase.Succeeded(stored(claimedAt), stored(searchStartedAt), stored(endedAt));
            case FAILED ->
                new Phase.Failed(
                        SolveFailure.ofStored(
                                Objects.requireNonNull(failureReason, "a failed job has its reason"),
                                failureViolations.stream()
                                        .map(StoredViolation::violation)
                                        .toList()),
                        stored(endedAt));
        };
    }

    /** Sets the kind and the times of a phase, and a failure's reason and violations, in place. */
    private void storePhase(Phase next) {
        claimedAt = null;
        searchStartedAt = null;
        endedAt = null;
        failureReason = null;
        failureViolations.clear();
        switch (next) {
            case Phase.Queued queued -> phase = PhaseKind.QUEUED;
            case Phase.BuildingMatrix building -> {
                phase = PhaseKind.BUILDING_MATRIX;
                claimedAt = building.claimedAt();
            }
            case Phase.Running running -> {
                phase = PhaseKind.RUNNING;
                claimedAt = running.claimedAt();
                searchStartedAt = running.searchStartedAt();
            }
            case Phase.Succeeded succeeded -> {
                phase = PhaseKind.SUCCEEDED;
                claimedAt = succeeded.claimedAt();
                searchStartedAt = succeeded.searchStartedAt();
                endedAt = succeeded.endedAt();
            }
            case Phase.Failed failed -> {
                phase = PhaseKind.FAILED;
                endedAt = failed.endedAt();
                failureReason = failed.failure().reason();
                failed.failure().violations().stream().map(StoredViolation::new).forEach(failureViolations::add);
            }
        }
    }

    /** Fills the result's lists Hibernate loaded, which are empty until a daily job succeeds. */
    private void storeResult(SolveResult kept) {
        StoredSolvedRoute.flatten(kept.routes(), resultRoutes, resultStops);
        kept.dropped().stream().map(StoredDroppedOrder::new).forEach(resultDropped::add);
        resultExcluded.addAll(kept.excluded());
    }

    private boolean keepsResult() {
        return lane == Lane.DAILY && phase == PhaseKind.SUCCEEDED;
    }

    private boolean storesAnyResult() {
        return !(resultRoutes.isEmpty()
                && resultStops.isEmpty()
                && resultDropped.isEmpty()
                && resultExcluded.isEmpty());
    }

    /** The time a command records: {@code now}, or the job's latest recorded time if that is later. */
    private Instant atOrAfterLatest(Instant now) {
        Instant latest = switch (phase()) {
            case Phase.BuildingMatrix building -> building.claimedAt();
            case Phase.Running running -> running.searchStartedAt();
            case Phase.Succeeded succeeded -> succeeded.endedAt();
            case Phase.Failed failed -> failed.endedAt();
            case Phase.Queued queued -> requestedAt;
        };
        return TimeOrder.notBefore(now, latest);
    }

    private void requireNotEnded() {
        if (phase == PhaseKind.SUCCEEDED || phase == PhaseKind.FAILED) {
            throw new RuleViolation(new JobAlreadyEnded(id()));
        }
    }

    private void requireLane(Lane expected) {
        if (lane != expected) {
            throw new RuleViolation(new ResultForAnotherLane(id(), lane));
        }
    }

    private Phase.Running requireRunning() {
        if (!(phase() instanceof Phase.Running running)) {
            throw new RuleViolation(new JobNotRunning(id()));
        }
        return running;
    }

    private void endSucceeded(Phase.Running running, Instant now) {
        storePhase(new Phase.Succeeded(running.claimedAt(), running.searchStartedAt(), now));
        JobId job = id();
        raise(SolveSucceeded.class, id, eventId -> new SolveSucceeded(eventId, now, job, plan, lane));
    }

    private void endFailed(SolveFailure failure, Instant now) {
        storePhase(new Phase.Failed(failure, now));
        JobId job = id();
        raise(SolveFailed.class, id, eventId -> new SolveFailed(eventId, now, job, plan, lane, failure.reason()));
    }

    /**
     * Checks the state a command left against the invariants at rest (ADR-126 decision 11). Every
     * input was checked before this point, so a broken invariant here is a bug in the command, not a
     * refusal, and the error rolls its transaction back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a solve job command left a state that breaks: " + broken);
        });
    }

    /**
     * The one check of the stored fields, shared by the load check and every command: the checks
     * {@link #restore} runs on a snapshot, in its order, and that only a failed job stores
     * violations. Rebuilding the phase and the result checks each stored value on the way.
     */
    private Optional<String> brokenRule() {
        boolean reoptimization = lane == Lane.REOPTIMIZATION;
        if (reoptimization != (basisRevision != null)) {
            return Optional.of(BASIS_ONLY_FOR_REOPTIMIZATION);
        }
        if (reoptimization && !limit.equals(SolveLimit.REOPTIMIZATION)) {
            return Optional.of(REOPTIMIZATION_LIMIT);
        }
        if (!keepsResult() && storesAnyResult()) {
            return Optional.of(RESULT_ONLY_WHEN_DAILY_SUCCEEDED);
        }
        if (phase != PhaseKind.FAILED && !failureViolations.isEmpty()) {
            return Optional.of(VIOLATIONS_ONLY_WHEN_FAILED);
        }
        if (!timesInOrder(requestedAt, phase())) {
            return Optional.of(TIMES_IN_ORDER);
        }
        result();
        return Optional.empty();
    }

    private static Instant stored(@Nullable Instant time) {
        return Objects.requireNonNull(time, "the phase keeps this time");
    }

    private static boolean timesInOrder(Instant requestedAt, Phase phase) {
        return switch (phase) {
            case Phase.Queued queued -> true;
            case Phase.BuildingMatrix building -> !building.claimedAt().isBefore(requestedAt);
            case Phase.Running running ->
                !running.claimedAt().isBefore(requestedAt)
                        && !running.searchStartedAt().isBefore(running.claimedAt());
            case Phase.Succeeded succeeded ->
                !succeeded.claimedAt().isBefore(requestedAt)
                        && !succeeded.searchStartedAt().isBefore(succeeded.claimedAt())
                        && !succeeded.endedAt().isBefore(succeeded.searchStartedAt());
            case Phase.Failed failed -> !failed.endedAt().isBefore(requestedAt);
        };
    }

    private static StoredStateInvalid invalid(String detail) {
        return new StoredStateInvalid(SolveJob.class, detail);
    }
}
