package com.dispatchflow.planning.application.port.out.solvejob;

import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.planning.domain.plan.LaneCapacity;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Planning's solve jobs, in memory, for application tests. It keeps the instances it is given:
 * {@link #add} publishes a job's events through {@code SaveEvents.publish} and stores it at version
 * 0, by {@link com.dispatchflow.shared.AsSaved#asSaved}, and {@link #capacityUnderLock} reads a
 * lane's queued and started jobs from them, in the order they were added, as the adapter does
 * under the lane's lock.
 */
public final class InMemorySolveJobRepository implements SolveJobRepository {

    private final Map<JobId, SolveJob> jobs = new LinkedHashMap<>();

    /**
     * Every job kept.
     *
     * @return the jobs, in the order they were added
     */
    public List<SolveJob> all() {
        return List.copyOf(jobs.values());
    }

    @Override
    public Optional<SolveJob> get(JobId id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public void add(SolveJob job) {
        if (job.savedVersion().isPresent()) {
            throw new IllegalArgumentException("add takes a job never saved");
        }
        asSaved(job);
        jobs.put(job.id(), job);
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void save(SolveJob job) {
        throw new UnsupportedOperationException("the in-memory jobs don't save a changed job yet");
    }

    @Override
    public LaneCapacity capacityUnderLock(Lane lane) {
        List<SolveJob> inLane =
                jobs.values().stream().filter(job -> job.lane() == lane).toList();
        List<PlanId> queued = inLane.stream()
                .filter(job -> job.phase() instanceof SolveJob.Phase.Queued)
                .map(SolveJob::plan)
                .toList();
        List<PlanId> started = inLane.stream()
                .filter(job -> job.phase() instanceof SolveJob.Phase.BuildingMatrix
                        || job.phase() instanceof SolveJob.Phase.Running)
                .map(SolveJob::plan)
                .toList();
        return LaneCapacity.readUnderLock(lane, queued, started);
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public Optional<SolveJob> claimNext(Lane lane) {
        throw new UnsupportedOperationException("the in-memory jobs don't claim a job yet");
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public List<JobId> unfinished() {
        throw new UnsupportedOperationException("the in-memory jobs don't list unfinished jobs yet");
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public List<JobId> unfinishedOn(DeliveryDate date) {
        throw new UnsupportedOperationException("the in-memory jobs don't list a date's unfinished jobs yet");
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public int blankCoordinatesDatedBefore(DeliveryDate cutoff) {
        throw new UnsupportedOperationException("the in-memory jobs keep no coordinates");
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public int blankPositionsReceivedBefore(Instant cutoff) {
        throw new UnsupportedOperationException("the in-memory jobs keep no positions");
    }
}
