package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.PlanId;
import java.util.List;
import java.util.Objects;

/**
 * A lane's queued and started jobs, read under the lane's advisory lock, which queueing a job
 * requires (BR-112, BR-38, DL §5). It also names each job's plan, so a queued or started
 * re-optimization of the same plan refuses a second one (ADR-101).
 *
 * <p>Only the repository query that takes the lock builds one; an ArchUnit rule allows no other
 * caller of {@link #readUnderLock}. So {@code SolveJob.queue} can't run without the lock.
 */
public final class LaneCapacity {

    /** At most this many jobs wait in one lane (BR-112). */
    public static final int QUEUE_LIMIT = 5;

    private final Lane lane;
    private final List<PlanId> queued;
    private final List<PlanId> started;

    private LaneCapacity(Lane lane, List<PlanId> queued, List<PlanId> started) {
        this.lane = Objects.requireNonNull(lane, "lane");
        this.queued = List.copyOf(queued);
        this.started = List.copyOf(started);
    }

    /**
     * Builds the snapshot; only the repository may call it, under the lane's lock.
     *
     * @param lane the lane
     * @param queued the plan of each queued job, in queue order
     * @param started the plan of each job building its matrix or running
     * @return the snapshot
     */
    public static LaneCapacity readUnderLock(Lane lane, List<PlanId> queued, List<PlanId> started) {
        return new LaneCapacity(lane, queued, started);
    }

    /**
     * The lane this snapshot was read for.
     *
     * @return the lane
     */
    public Lane lane() {
        return lane;
    }

    boolean isFull() {
        return queued.size() >= QUEUE_LIMIT;
    }

    boolean holdsAJobOf(PlanId plan) {
        return queued.contains(plan) || started.contains(plan);
    }
}
