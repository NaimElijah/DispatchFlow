package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.domain.plan.LaneCapacity;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where solve jobs are kept, the {@code solve_job} queue, with each job's input, result and failure
 * (DL §3.6, §9.7, ADR-051, ADR-052). The persistence adapter loads a job whole, which its
 * {@code @PostLoad} check checks (ADR-126), and stores the events it raised with the change
 * (ADR-064).
 *
 * <p>The queue is built in M0; in full in M4. M0 builds the daily lane's queue, and the roadmap's
 * M4 builds "the {@code solve_job} queue, one worker per lane" (ADR-051).
 *
 * <p>Built in M0, M4, M5 and M13, method by method.
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
     * @param job the job, never saved
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code
     *     ReoptimizationAlreadyRunning} when the index finds another, a race the lock missed
     */
    void add(SolveJob job);

    /**
     * Saves a changed job with its version check, and stores the events it raised, in the caller's
     * transaction (BR-76). Its worker, the watchdog, startup recovery and the midnight job may
     * race; the loser's save fails (DL §11).
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
     * Every job of a date's plans that is queued, building its matrix or running, which the
     * midnight job fails first (BR-128).
     *
     * <p>Built in M5.
     *
     * @param date the date that just ended
     * @return the jobs, oldest first
     */
    List<JobId> unfinishedOn(DeliveryDate date);

    /**
     * Blanks every coordinate in the stored inputs of the solve jobs of every plan dated before a
     * cutoff, in the caller's transaction: the orders' and the depots' locations, the matrix's list
     * of locations, and a re-optimization's positions with each of its position rows whole, its
     * seconds and metres included, because a row's travel times to known places locate its position
     * (NFR-257, NFR-260, ADR-121). The inputs keep everything else, so a job's result and failure
     * still read. An input already blanked is left as it is, so a second run blanks nothing.
     *
     * <p>Built in M13.
     *
     * @param cutoff the first date whose plans' jobs keep their coordinates: today less 90 Israel
     *     calendar days (ADR-088)
     * @return how many jobs' inputs were blanked
     */
    int blankCoordinatesDatedBefore(DeliveryDate cutoff);

    /**
     * Blanks every vehicle position received before a cutoff in the stored inputs of
     * re-optimization jobs, with the position row that starts from it, whole: its point, seconds
     * and metres, because a row's travel times to known places locate its position. It runs in the
     * caller's transaction, as Live Operations deletes the positions themselves (NFR-184, NFR-260,
     * ADR-121). A position already blanked is left as it is.
     *
     * <p>Built in M13.
     *
     * @param cutoff the first receipt time whose positions are kept: now less 7 Israel calendar
     *     days, the same wall-clock time on the date 7 days earlier (ADR-088)
     * @return how many jobs' inputs had a position blanked
     */
    int blankPositionsReceivedBefore(Instant cutoff);
}
