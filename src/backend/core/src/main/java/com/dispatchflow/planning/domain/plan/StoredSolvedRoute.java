package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.SolvedStop;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link SolvedRoute} is stored in, a row of its owner's route table, without its
 * stops: those go to a stop table of their own, keyed by both positions, because a list inside a
 * list can't be an embeddable's (ADR-127 decision 3). {@link #flatten} splits a solution's routes
 * into the two lists and {@link #assemble} puts them back. A solve job's result and a proposal
 * store their routes this way.
 */
@Embeddable
class StoredSolvedRoute {

    private static final String STOP_ON_A_ROUTE = "a solved stop is on one of its solution's routes";
    private static final String STOPS_IN_ORDER = "a solved route's stops are stored in their order";

    @Column(name = "vehicle_id")
    private VehicleId vehicle;

    @Column(name = "driver_id")
    private AccountId driver;

    @Embedded
    private @Nullable StoredRoutePlan plan;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredSolvedRoute() {}

    private StoredSolvedRoute(SolvedRoute route) {
        this.vehicle = route.vehicle();
        this.driver = route.driver();
        this.plan = route.plan().map(StoredRoutePlan::new).orElse(null);
    }

    /**
     * Stores a solution's routes in two mapped lists, which the caller loaded or just created: its
     * routes in order, and every route's stops after the stops of the routes before it.
     *
     * @param solved the routes
     * @param routes the mapped list of routes, which this fills
     * @param stops the mapped list of stops, which this fills
     */
    static void flatten(List<SolvedRoute> solved, List<StoredSolvedRoute> routes, List<StoredSolvedStop> stops) {
        for (int r = 0; r < solved.size(); r++) {
            SolvedRoute route = solved.get(r);
            routes.add(new StoredSolvedRoute(route));
            for (int s = 0; s < route.stops().size(); s++) {
                stops.add(new StoredSolvedStop(r, s, route.stops().get(s)));
            }
        }
    }

    /**
     * The routes two stored lists hold, each with its stops in order.
     *
     * @param routes the stored routes, in order
     * @param stops the stored stops, each route's in order
     * @return the routes
     * @throws StoredStateInvalid when a stop names no stored route, or a route's stops are stored out
     *     of order or with a gap, or a stored value is refused
     */
    static List<SolvedRoute> assemble(List<StoredSolvedRoute> routes, List<StoredSolvedStop> stops) {
        List<List<SolvedStop>> stopsOf = new ArrayList<>();
        routes.forEach(route -> stopsOf.add(new ArrayList<>()));
        for (StoredSolvedStop stop : stops) {
            if (stop.routePosition() < 0 || stop.routePosition() >= routes.size()) {
                throw new StoredStateInvalid(SolvedRoute.class, STOP_ON_A_ROUTE);
            }
            List<SolvedStop> on = stopsOf.get(stop.routePosition());
            if (stop.stopPosition() != on.size()) {
                throw new StoredStateInvalid(SolvedRoute.class, STOPS_IN_ORDER);
            }
            on.add(stop.solvedStop());
        }
        return IntStream.range(0, routes.size())
                .mapToObj(r -> routes.get(r).solvedRoute(stopsOf.get(r)))
                .toList();
    }

    private SolvedRoute solvedRoute(List<SolvedStop> stops) {
        return new SolvedRoute(vehicle, driver, Optional.ofNullable(plan).map(StoredRoutePlan::routePlan), stops);
    }
}
