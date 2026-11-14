package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.CapacityForAnotherLane;
import com.dispatchflow.planning.domain.PlanningError.JobAlreadyEnded;
import com.dispatchflow.planning.domain.PlanningError.JobNotBuildingMatrix;
import com.dispatchflow.planning.domain.PlanningError.JobNotQueued;
import com.dispatchflow.planning.domain.PlanningError.JobNotRunning;
import com.dispatchflow.planning.domain.PlanningError.ReoptimizationAlreadyRunning;
import com.dispatchflow.planning.domain.PlanningError.ResultForAnotherLane;
import com.dispatchflow.planning.domain.PlanningError.SolveQueueFull;
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
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
 */
public final class SolveJob extends AggregateRoot {

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

    private final JobId id;
    private final PlanId plan;
    private final Lane lane;
    private final SolverName solver;
    private final SolveLimit limit;
    private final Seed seed;
    private final @Nullable RevisionNo basisRevision;
    private final Instant requestedAt;
    private Phase phase;
    private @Nullable SolveResult result;

    private SolveJob(Version version, SolveJobSnapshot state) {
        super(version);
        this.id = state.id();
        this.plan = state.plan();
        this.lane = state.lane();
        this.solver = state.solver();
        this.limit = state.limit();
        this.seed = state.seed();
        this.basisRevision = state.basisRevision().orElse(null);
        this.requestedAt = state.requestedAt();
        this.phase = state.phase();
        this.result = state.result().orElse(null);
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
     * @param basisRevision the revision a re-optimization starts from; {@code null} for a daily solve
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
            @Nullable RevisionNo basisRevision,
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
        return new SolveJob(
                Version.NEW,
                new SolveJobSnapshot(
                        id,
                        Version.NEW,
                        plan,
                        lane,
                        solver,
                        limit,
                        seed,
                        Optional.ofNullable(basisRevision),
                        now,
                        new Phase.Queued(),
                        Optional.empty()));
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
            throw invalid("a basis revision belongs to a re-optimization, and only to one");
        }
        if (reoptimization && !snapshot.limit().equals(SolveLimit.REOPTIMIZATION)) {
            throw invalid("a re-optimization's limit is 20 seconds");
        }
        boolean keepsResult = snapshot.lane() == Lane.DAILY && snapshot.phase() instanceof Phase.Succeeded;
        if (keepsResult != snapshot.result().isPresent()) {
            throw invalid("a result belongs to a succeeded daily job, and only to one");
        }
        if (!timesInOrder(snapshot.requestedAt(), snapshot.phase())) {
            throw invalid("the phase's times are out of order");
        }
        return new SolveJob(snapshot.version(), snapshot);
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
        if (!(phase instanceof Phase.Queued)) {
            throw new RuleViolation(new JobNotQueued(id));
        }
        phase = new Phase.BuildingMatrix(now);
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
        if (!(phase instanceof Phase.BuildingMatrix building)) {
            throw new RuleViolation(new JobNotBuildingMatrix(id));
        }
        phase = new Phase.Running(building.claimedAt(), now);
    }

    /**
     * A daily solve returned a validated solution, which the job keeps for its plan (BR-15). Raises
     * {@link SolveSucceeded}.
     *
     * @param solution the validated solution
     * @param now when it ended
     * @throws RuleViolation with {@link JobAlreadyEnded} once ended, {@link ResultForAnotherLane} for a
     *     re-optimization, or {@link JobNotRunning} when its search is not running
     */
    public void succeed(ValidatedSolution solution, Instant now) {
        requireNotEnded();
        requireLane(Lane.DAILY);
        Phase.Running running = requireRunning();
        result = new SolveResult(solution.routes(), solution.dropped());
        endSucceeded(running, now);
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
        ReoptimizationProposal proposal = ReoptimizationProposal.create(
                proposalId, plan, id, new ProposalBasis(revision, reoptimization.factsAt()), reoptimization, now);
        endSucceeded(running, now);
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
        endFailed(failure, now);
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
        if (phase instanceof Phase.BuildingMatrix building) {
            deadline = building.claimedAt().plus(MATRIX_DEADLINE);
            reason = SolveFailureReason.MATRIX_TOO_LONG;
        } else if (phase instanceof Phase.Running running) {
            deadline = running.searchStartedAt().plus(limit.duration()).plus(OVERRUN_GRACE);
            reason = SolveFailureReason.OVERRAN;
        } else {
            deadline = requestedAt.plus(QUEUE_WAIT);
            reason = SolveFailureReason.QUEUED_TOO_LONG;
        }
        if (now.isAfter(deadline)) {
            endFailed(SolveFailure.of(reason), now);
        }
    }

    /**
     * The job's ID.
     *
     * @return the ID
     */
    public JobId id() {
        return id;
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
        return Optional.ofNullable(result);
    }

    /**
     * The job's state, for the repository and for tests.
     *
     * @return a snapshot that {@link #restore} rebuilds
     */
    public SolveJobSnapshot snapshot() {
        return new SolveJobSnapshot(
                id,
                version(),
                plan,
                lane,
                solver,
                limit,
                seed,
                Optional.ofNullable(basisRevision),
                requestedAt,
                phase,
                Optional.ofNullable(result));
    }

    private void requireNotEnded() {
        if (phase instanceof Phase.Succeeded || phase instanceof Phase.Failed) {
            throw new RuleViolation(new JobAlreadyEnded(id));
        }
    }

    private void requireLane(Lane expected) {
        if (lane != expected) {
            throw new RuleViolation(new ResultForAnotherLane(id, lane));
        }
    }

    private Phase.Running requireRunning() {
        if (!(phase instanceof Phase.Running running)) {
            throw new RuleViolation(new JobNotRunning(id));
        }
        return running;
    }

    private void endSucceeded(Phase.Running running, Instant now) {
        phase = new Phase.Succeeded(running.claimedAt(), running.searchStartedAt(), now);
        raise(SolveSucceeded.class, id.value(), eventId -> new SolveSucceeded(eventId, now, id, plan, lane));
    }

    private void endFailed(SolveFailure failure, Instant now) {
        phase = new Phase.Failed(failure, now);
        raise(
                SolveFailed.class,
                id.value(),
                eventId -> new SolveFailed(eventId, now, id, plan, lane, failure.reason()));
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
