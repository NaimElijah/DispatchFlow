package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.domain.PlanningError.ReoptimizationAlreadyRunning;
import com.dispatchflow.planning.domain.plan.LaneCapacity;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps solve jobs in {@code planning.solve_job}, with a failure's violations and a daily result's
 * routes, stops, drops and excluded vehicles in a table each.
 *
 * <p>The partial unique index on a plan's queued or started re-optimization backs BR-38 when two
 * requests race past the lane's lock: {@code add} answers its violation with {@link
 * ReoptimizationAlreadyRunning}, and lets every other constraint violation through.
 */
@Component
@Transactional
class JpaSolveJobRepository implements SolveJobRepository {

    /** The migration's name for the partial unique index of BR-38. */
    private static final String ONE_REOPTIMIZATION_PER_PLAN = "solve_job_one_reoptimization_per_plan";

    private final SolveJobs jobs;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param jobs the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which {@code add} flushes
     */
    JpaSolveJobRepository(SolveJobs jobs, VersionedSave versionedSave, EntityManager entityManager) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<SolveJob> get(JobId id) {
        return load(id.value());
    }

    /**
     * {@inheritDoc}
     *
     * <p>It flushes at once, so the partial unique index answers inside this call.
     */
    @Override
    public void add(SolveJob job) {
        try {
            jobs.save(job);
            entityManager.flush();
        } catch (ConstraintViolationException | DataIntegrityViolationException violation) {
            throw alreadyRunningOr(violation, job.plan());
        }
    }

    @Override
    public void save(SolveJob job) {
        versionedSave.save(job, jobs::save);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The lane's advisory lock comes first, so two requests that both find room still queue one
     * after the other (ADR-127 decision 9).
     */
    @Override
    public LaneCapacity capacityUnderLock(Lane lane) {
        jobs.lockLane(lane.name());
        return LaneCapacity.readUnderLock(
                lane, plans(jobs.queuedPlans(lane.name())), plans(jobs.startedPlans(lane.name())));
    }

    /**
     * {@inheritDoc}
     *
     * <p>It skips a queued job another worker's transaction holds, so two claims never wait on each
     * other, and then loads the one it locked whole.
     */
    @Override
    public Optional<SolveJob> claimNext(Lane lane) {
        return jobs.lockOldestQueued(lane.name()).flatMap(this::load);
    }

    @Override
    public List<JobId> unfinished() {
        return jobs.unfinished().stream().map(JobId::of).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>A job keeps no date of its own, so it is found through its plan's date.
     */
    @Override
    public List<JobId> unfinishedOn(DeliveryDate date) {
        return jobs.unfinishedOn(date.value()).stream().map(JobId::of).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not built yet: nothing stores a job's input, which is what it blanks.
     *
     * @throws UnsupportedOperationException always, until then
     */
    @Override
    public int blankCoordinatesDatedBefore(DeliveryDate cutoff) {
        throw new UnsupportedOperationException("Built in M13");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not built yet: nothing stores a job's input, which is what it blanks.
     *
     * @throws UnsupportedOperationException always, until then
     */
    @Override
    public int blankPositionsReceivedBefore(Instant cutoff) {
        throw new UnsupportedOperationException("Built in M13");
    }

    /** Loads a job whole: its violations with it, then each result collection into the same instance. */
    private Optional<SolveJob> load(UUID id) {
        Optional<SolveJob> job = jobs.findById(id);
        if (job.isPresent()) {
            jobs.fetchResultRoutes(id);
            jobs.fetchResultStops(id);
            jobs.fetchResultDropped(id);
            jobs.fetchResultExcluded(id);
        }
        return job;
    }

    private static List<PlanId> plans(List<UUID> keys) {
        return keys.stream().map(PlanId::of).toList();
    }

    /**
     * {@link ReoptimizationAlreadyRunning} for a violation of BR-38's index, and the violation itself
     * for any other, which is a bug.
     */
    private static RuntimeException alreadyRunningOr(RuntimeException violation, PlanId plan) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && ONE_REOPTIMIZATION_PER_PLAN.equals(constraint.getConstraintName())) {
                return new RuleViolation(new ReoptimizationAlreadyRunning(plan));
            }
        }
        return violation;
    }
}
