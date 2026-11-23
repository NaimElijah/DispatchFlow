package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.plan.SolveJob;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaSolveJobRepository}: {@code save}, the loads that fetch
 * a job's five collections one query each (ADR-126), and the native statements of the queue: the
 * lane's advisory lock and the claim with {@code FOR UPDATE SKIP LOCKED}. A lane is bound by its
 * name, as its column stores it. The fetches run whatever the job's load check read: loading whole
 * must not depend on it, because Jakarta Persistence calls touching a relationship in a lifecycle
 * callback unportable.
 */
interface SolveJobs extends Repository<SolveJob, UUID> {

    /**
     * Saves a job.
     *
     * @param job the job
     * @return the managed job
     */
    SolveJob save(SolveJob job);

    /**
     * Loads a job with its failure's violations.
     *
     * @param id the job's UUID
     * @return the job, or empty
     */
    @EntityGraph(attributePaths = "failureViolations")
    Optional<SolveJob> findById(UUID id);

    /**
     * Fetches a job's result routes into the job this transaction already holds, whether or not its
     * load check read them.
     *
     * @param id the job's UUID
     * @return the job, or empty
     */
    @Query("select j from SolveJob j left join fetch j.resultRoutes where j.id = :id")
    Optional<SolveJob> fetchResultRoutes(UUID id);

    /**
     * Fetches a job's result stops into the job this transaction already holds, whether or not its
     * load check read them.
     *
     * @param id the job's UUID
     * @return the job, or empty
     */
    @Query("select j from SolveJob j left join fetch j.resultStops where j.id = :id")
    Optional<SolveJob> fetchResultStops(UUID id);

    /**
     * Fetches a job's result drops into the job this transaction already holds, whether or not its
     * load check read them.
     *
     * @param id the job's UUID
     * @return the job, or empty
     */
    @Query("select j from SolveJob j left join fetch j.resultDropped where j.id = :id")
    Optional<SolveJob> fetchResultDropped(UUID id);

    /**
     * Fetches a job's result's excluded vehicles into the job this transaction already holds,
     * whether or not its load check read them.
     *
     * @param id the job's UUID
     * @return the job, or empty
     */
    @Query("select j from SolveJob j left join fetch j.resultExcluded where j.id = :id")
    Optional<SolveJob> fetchResultExcluded(UUID id);

    /**
     * Takes a lane's advisory lock for the caller's transaction, waiting for it (ADR-127 decision 9).
     *
     * @param lane the lane's name
     * @return 1, always: the call is made for its lock
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('planning:solve-lane:' || :lane))", nativeQuery = true)
    int lockLane(String lane);

    /**
     * The plan of each queued job of a lane.
     *
     * @param lane the lane's name
     * @return the plans' UUIDs, in queue order: the oldest request first
     */
    @Query(value = """
            SELECT plan_id FROM planning.solve_job
            WHERE lane = :lane AND phase = 'QUEUED'
            ORDER BY requested_at, id
            """, nativeQuery = true)
    List<UUID> queuedPlans(String lane);

    /**
     * The plan of each job of a lane that is building its matrix or running.
     *
     * @param lane the lane's name
     * @return the plans' UUIDs, the oldest request first
     */
    @Query(value = """
            SELECT plan_id FROM planning.solve_job
            WHERE lane = :lane AND phase IN ('BUILDING_MATRIX', 'RUNNING')
            ORDER BY requested_at, id
            """, nativeQuery = true)
    List<UUID> startedPlans(String lane);

    /**
     * Locks a lane's oldest queued job that no other transaction has locked, for the caller's
     * transaction, without waiting for the ones that are (ADR-051, TS §6).
     *
     * @param lane the lane's name
     * @return the job's UUID, or empty when every queued job of the lane is locked or there is none
     */
    @Query(value = """
            SELECT id FROM planning.solve_job
            WHERE lane = :lane AND phase = 'QUEUED'
            ORDER BY requested_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> lockOldestQueued(String lane);

    /**
     * Every job queued, building its matrix or running, in any lane.
     *
     * @return the jobs' UUIDs, the oldest request first
     */
    @Query(value = """
            SELECT id FROM planning.solve_job
            WHERE phase IN ('QUEUED', 'BUILDING_MATRIX', 'RUNNING')
            ORDER BY requested_at, id
            """, nativeQuery = true)
    List<UUID> unfinished();

    /**
     * Every job queued, building its matrix or running, in any lane, of a plan of a date. A job keeps
     * no date of its own, so the date is its plan's, in Planning's own plan table.
     *
     * @param date the plans' delivery date
     * @return the jobs' UUIDs, the oldest request first
     */
    @Query(value = """
            SELECT j.id FROM planning.solve_job j
            JOIN planning.plan p ON p.id = j.plan_id
            WHERE p.delivery_date = :date AND j.phase IN ('QUEUED', 'BUILDING_MATRIX', 'RUNNING')
            ORDER BY j.requested_at, j.id
            """, nativeQuery = true)
    List<UUID> unfinishedOn(LocalDate date);
}
