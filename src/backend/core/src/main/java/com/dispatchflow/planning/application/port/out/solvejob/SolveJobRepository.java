package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.domain.plan.LaneCapacity;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Optional;

/**
 * Where solve jobs are kept, the {@code solve_job} queue, with each job's input, result and failure
 * (DL §3.6, §9.7, ADR-051, ADR-052). The persistence adapter loads a job through {@code
 * SolveJob.restore}, and stores the events it raised with the change (ADR-064).
 *
 * <p>Built in M0, M4 and M5, method by method.
 */
public interface SolveJobRepository {

    /**
     * One job.
     *
     * <p>Built in M0.
     *
     * @param id the job
     * @return the job, or empty when no job has the ID
     */
    Optional<SolveJob> get(JobId id);

    /**
     * Adds a newly queued job, in the caller's transaction, beside the change that created it (DL
     * §6.1). A partial unique index backs one queued or started re-optimization per plan (BR-38).
     *
     * <p>Built in M0.
     *
     * @param job the job, at {@code Version.NEW}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code
     *     ReoptimizationAlreadyRunning} when the index finds another, a race the lock missed
     */
    void add(SolveJob job);

    /**
     * Saves a changed job with its version check, and stores the events it raised, in the caller's
     * transaction (BR-76). Its worker, the watchdog, startup recovery and the midnight job may race;
     * the loser's save fails (DL §11).
     *
     * <p>Built in M0.
     *
     * @param job the job, at the version it was loaded at
     * @throws StaleVersion when the job changed since it was loaded
     */
    void save(SolveJob job);

    /**
     * A lane's queued and started jobs, read under the lane's advisory lock, which the caller's
     * transaction holds until it commits (BR-38, BR-112, DL §5). It is the only caller of {@code
     * LaneCapacity.readUnderLock}.
     *
     * <p>Built in M0.
     *
     * @param lane the lane a new job joins
     * @return the lane's capacity
     */
    LaneCapacity capacityUnderLock(Lane lane);

    /**
     * The lane's oldest queued job, locked for the caller's transaction so no other worker takes it
     * (ADR-051). The caller claims it in that transaction.
     *
     * <p>Built in M0.
     *
     * @param lane the worker's lane
     * @return the job, or empty when the lane has none queued
     */
    Optional<SolveJob> claimNext(Lane lane);

    /**
     * Every job queued, building its matrix or running, in any lane, which the watchdog and startup
     * recovery check (BR-32, BR-74, BR-85, BR-112, NFR-12).
     *
     * <p>Built in M4.
     *
     * @return the jobs, oldest first
     */
    List<JobId> unfinished();

    /**
     * Every job of a date's plans that is queued, building its matrix or running, which the midnight
     * job fails first (BR-128).
     *
     * <p>Built in M5.
     *
     * @param date the date that just ended
     * @return the jobs, oldest first
     */
    List<JobId> unfinishedOn(DeliveryDate date);
}
