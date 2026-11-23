package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.domain.routerun.RouteRun;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps each route's {@link RouteRun} in {@code liveops.route_run} and the stops a finished run left
 * pending in {@code liveops.route_run_pending_stop}.
 *
 * <p>The route's delivery date is a column of the run's row that the run does not map: {@link #add}
 * writes it with one native {@code UPDATE} once the run is inserted, in the same transaction, and
 * {@link #startedOn} reads it. The column is nullable for that reason, so the schema can't refuse a
 * run stored without its date; only this adapter writes the table.
 */
@Component
@Transactional
class JpaRouteRunRepository implements RouteRunRepository {

    private final RouteRuns runs;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param runs the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaRouteRunRepository(RouteRuns runs, VersionedSave versionedSave) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public Optional<RouteRun> get(RouteId route) {
        return runs.findById(route.value());
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which turns a first save that loses the race for the
     * route's key into {@code StaleVersion}, and then keeps the date beside the run.
     *
     * @throws AssertionError when the run was stored before, so its row already has a date: the
     *     caller added a run it had loaded
     */
    @Override
    public void add(RouteRun run, DeliveryDate date) {
        Objects.requireNonNull(date, "date");
        versionedSave.save(run, runs::save);
        if (runs.keepDate(run.route().value(), date.value()) != 1) {
            throw new AssertionError("a route run is added once, and its date kept with it");
        }
    }

    @Override
    public void save(RouteRun run) {
        versionedSave.save(run, runs::save);
    }

    @Override
    public List<RouteRun> startedOn(DeliveryDate date) {
        List<UUID> routes = runs.startedOn(date.value());
        return routes.isEmpty() ? List.of() : runs.findByIdIn(routes);
    }
}
