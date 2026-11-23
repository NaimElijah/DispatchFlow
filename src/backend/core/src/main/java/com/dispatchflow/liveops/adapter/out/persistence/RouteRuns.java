package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.routerun.RouteRun;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaRouteRunRepository}: {@code save}, the lookups the port
 * needs, and the two native statements over the delivery date the adapter keeps beside each run.
 */
interface RouteRuns extends Repository<RouteRun, UUID> {

    /**
     * Saves a run and publishes its pending events.
     *
     * @param run the run
     * @return the managed run
     */
    RouteRun save(RouteRun run);

    /**
     * Loads a run whole, its pending stops with it.
     *
     * @param route the route's UUID
     * @return the run, or empty
     */
    @EntityGraph(attributePaths = "pendingStops")
    Optional<RouteRun> findById(UUID route);

    /**
     * Loads several runs whole.
     *
     * @param routes the routes' UUIDs
     * @return the runs found, each once, in no order
     */
    @EntityGraph(attributePaths = "pendingStops")
    List<RouteRun> findByIdIn(Collection<UUID> routes);

    /**
     * Keeps the delivery date of a run just stored, a column the run does not map.
     *
     * @param route the run's route UUID
     * @param date the route's delivery date
     * @return 1 when the run was stored without its date, 0 otherwise
     */
    @Modifying
    @Query(
            value = "UPDATE liveops.route_run SET delivery_date = :date WHERE id = :route AND delivery_date IS NULL",
            nativeQuery = true)
    int keepDate(UUID route, LocalDate date);

    /**
     * The runs of a date's routes.
     *
     * @param date the delivery date
     * @return the runs' route UUIDs, in no order
     */
    @Query(value = "SELECT id FROM liveops.route_run WHERE delivery_date = :date", nativeQuery = true)
    List<UUID> startedOn(LocalDate date);
}
