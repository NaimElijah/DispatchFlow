package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RuleViolation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Queues solve jobs for tests outside this package, such as the persistence integration tests,
 * through {@link SolveJob#queue}, which only {@link Plan} calls in production. Every later state is
 * reached through the job's own public commands.
 */
public final class QueuedJobs {

    /** The basis revision a queued re-optimization starts from. */
    public static final RevisionNo BASIS = RevisionNo.FIRST;

    /** The seed every queued job runs with. */
    public static final Seed SEED = Seed.of(7);

    private QueuedJobs() {}

    /**
     * Queues a job with OR-Tools: a daily job with the default limit, or a re-optimization with its
     * 20 seconds and {@link #BASIS}.
     *
     * @param id the job's ID
     * @param plan the plan it solves
     * @param lane its lane
     * @param capacity the lane's queue, as the repository read it under its lock
     * @param now the time of the request
     * @return the queued job, never saved
     * @throws RuleViolation with what {@link SolveJob#queue} refuses
     */
    public static SolveJob queue(JobId id, PlanId plan, Lane lane, LaneCapacity capacity, Instant now) {
        SolveLimit limit = lane == Lane.DAILY ? SolveLimit.DEFAULT_DAILY : SolveLimit.REOPTIMIZATION;
        Optional<RevisionNo> basis = lane == Lane.DAILY ? Optional.empty() : Optional.of(BASIS);
        return SolveJob.queue(id, plan, lane, SolverName.OR_TOOLS, limit, SEED, basis, capacity, now);
    }

    /**
     * Queues a job in a lane with room for it.
     *
     * @param id the job's ID
     * @param plan the plan it solves
     * @param lane its lane
     * @param now the time of the request
     * @return the queued job, never saved
     */
    public static SolveJob queue(JobId id, PlanId plan, Lane lane, Instant now) {
        return queue(id, plan, lane, LaneCapacity.readUnderLock(lane, List.of(), List.of()), now);
    }
}
