package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.routerun.RouteRun;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Optional;

/**
 * Where each route's {@link RouteRun} is kept, keyed by its route (DL §3.6, §4.5). A route with no
 * run is <em>not started</em> (FR-244). Adding a run publishes its {@code RouteStarted}, in the
 * caller's transaction (ADR-064).
 *
 * <p>A run does not hold its route's delivery date, and no SQL reads Planning's schema (DL §2.3 rule
 * 3), so {@link #add} takes the date from Planning's answer and the adapter keeps it beside the run
 * for {@link #startedOn}.
 *
 * <p>Built in M5 and M8, method by method.
 */
public interface RouteRunRepository {

    /**
     * One route's run.
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @return the run, or empty when the route has not started
     */
    Optional<RouteRun> get(RouteId route);

    /**
     * Stores a route's new run, in the caller's transaction.
     *
     * <p>Built in M5.
     *
     * @param run the run, never saved
     * @param date the delivery date of the run's route, from Planning's {@code PublishedRoutes}
     * @throws StaleVersion when the route got a run meanwhile, as when two first reports arrive at
     *     once; the second is retried as a change (DL §11)
     */
    void add(RouteRun run, DeliveryDate date);

    /**
     * Stores a changed run, checking the version it was loaded at (BR-76, ADR-057).
     *
     * <p>Built in M5.
     *
     * @param run the run
     * @throws StaleVersion when another writer saved it since it was loaded
     */
    void save(RouteRun run);

    /**
     * Every run of a date's routes, started or finished.
     *
     * <p>Built in M8.
     *
     * @param date the date
     * @return the runs, each once; a route left out has not started
     */
    List<RouteRun> startedOn(DeliveryDate date);
}
