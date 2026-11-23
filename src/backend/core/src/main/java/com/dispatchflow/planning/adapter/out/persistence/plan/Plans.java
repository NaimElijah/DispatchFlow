package com.dispatchflow.planning.adapter.out.persistence.plan;

import com.dispatchflow.planning.domain.plan.Plan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaPlanRepository}: {@code save}, the loads that fetch a
 * plan's routes with their stops in one query and each of its seven other collections in one query
 * each (ADR-126), the date's advisory lock, and the native reads by date and by route check. A
 * date is bound as its {@code LocalDate}, and a status by its name, as their columns store them.
 * The fetches run whatever the plan's load check read: loading whole must not depend on it,
 * because Jakarta Persistence calls touching a relationship in a lifecycle callback unportable.
 */
interface Plans extends Repository<Plan, UUID> {

    /**
     * Saves a plan.
     *
     * @param plan the plan
     * @return the managed plan
     */
    Plan save(Plan plan);

    /**
     * Loads a plan with its routes and their stops. A route's stops are nested in it, so the one
     * query returns a row per stop, not a product of the two lists.
     *
     * @param id the plan's UUID
     * @return the plan, or empty
     */
    @EntityGraph(attributePaths = {"routes", "routes.stops"})
    Optional<Plan> findById(UUID id);

    /**
     * Fetches the plan's own collection of its stops into the plan this transaction already holds,
     * whether or not its load check read them; they are the stops its routes already loaded.
     *
     * @param id the plan's UUID
     * @return the plan, or empty
     */
    @Query("select p from Plan p left join fetch p.stops where p.id = :id")
    Optional<Plan> fetchStops(UUID id);

    /**
     * Fetches a plan's drops into the plan this transaction already holds, whether or not its load
     * check read them.
     *
     * @param id the plan's UUID
     * @return the plan, or empty
     */
    @Query("select p from Plan p left join fetch p.dropped where p.id = :id")
    Optional<Plan> fetchDropped(UUID id);

    /**
     * Fetches a plan's excluded vehicles into the plan this transaction already holds, whether or
     * not its load check read them.
     *
     * @param id the plan's UUID
     * @return the plan, or empty
     */
    @Query("select p from Plan p left join fetch p.excluded where p.id = :id")
    Optional<Plan> fetchExcluded(UUID id);

    /**
     * Fetches a plan's pins into the plan this transaction already holds, whether or not its load
     * check read them.
     *
     * @param id the plan's UUID
     * @return the plan, or empty
     */
    @Query("select p from Plan p left join fetch p.pins where p.id = :id")
    Optional<Plan> fetchPins(UUID id);

    /**
     * Fetches a plan's open violations into the plan this transaction already holds, whether or not
     * its load check read them.
     *
     * @param id the plan's UUID
     * @return the plan, or empty
     */
    @Query("select p from Plan p left join fetch p.violations where p.id = :id")
    Optional<Plan> fetchViolations(UUID id);

    /**
     * Fetches a plan's undo journal into the plan this transaction already holds, whether or not
     * its load check read them.
     *
     * @param id the plan's UUID
     * @return the plan, or empty
     */
    @Query("select p from Plan p left join fetch p.undo where p.id = :id")
    Optional<Plan> fetchUndo(UUID id);

    /**
     * Fetches a plan's redo journal into the plan this transaction already holds, whether or not
     * its load check read them.
     *
     * @param id the plan's UUID
     * @return the plan, or empty
     */
    @Query("select p from Plan p left join fetch p.redo where p.id = :id")
    Optional<Plan> fetchRedo(UUID id);

    /**
     * Takes a date's advisory lock for the caller's transaction, waiting for it (ADR-127 decision 9).
     * The date is bound as its ISO text, so the key doesn't depend on the session's date style.
     *
     * @param date the delivery date, as {@code yyyy-mm-dd}
     * @return 1, always: the call is made for its lock
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('planning:plan-date:' || :date))", nativeQuery = true)
    int lockDate(String date);

    /**
     * The date's uncancelled plan, of which the partial unique index keeps at most one (BR-34).
     *
     * @param date the delivery date
     * @return the plan's UUID, or empty
     */
    @Query(value = """
            SELECT id FROM planning.plan
            WHERE delivery_date = :date AND status <> 'CANCELLED'
            """, nativeQuery = true)
    Optional<UUID> uncancelledOn(LocalDate date);

    /**
     * The date's plan that is draft, solving, ready or published, of which there is at most one,
     * because those are all uncancelled (BR-34).
     *
     * @param date the delivery date
     * @return the plan's UUID, or empty
     */
    @Query(value = """
            SELECT id FROM planning.plan
            WHERE delivery_date = :date AND status IN ('DRAFT', 'SOLVING', 'READY', 'PUBLISHED')
            """, nativeQuery = true)
    Optional<UUID> unfinishedOn(LocalDate date);

    /**
     * The ready and published plans with a route not yet checked (FR-395).
     *
     * @return the plans' UUIDs, each once, by date and then by key
     */
    @Query(value = """
            SELECT p.id FROM planning.plan p
            WHERE p.status IN ('READY', 'PUBLISHED')
              AND EXISTS (
                  SELECT 1 FROM planning.route r
                  WHERE r.plan_id = p.id AND r.check_state = 'NOT_YET_CHECKED')
            ORDER BY p.delivery_date, p.id
            """, nativeQuery = true)
    List<UUID> withUncheckedRoutes();
}
