package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.RouteChangesView;
import com.dispatchflow.planning.application.port.out.plan.LoadingListView;
import com.dispatchflow.planning.application.port.out.plan.PrintableRouteView;
import com.dispatchflow.planning.domain.plan.RouteIndex;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.NotFound;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only transactions of {@link RouteQueries}: each screen's reads of Planning's views and
 * of the stops' and depots' details, with the data scope of BR-02 and BR-41, and the points a
 * route's line runs through, which {@link RouteQueries} sends to the road network (TS §6.2,
 * ADR-129).
 *
 * <p>Built in M3 and later milestones, method by method.
 */
@Transactional(readOnly = true)
public class RouteQueriesChange {

    private final PlanningViews views;
    private final OrdersForDate ordersForDate;
    private final StopOrders stopOrders;
    private final FleetFacts fleetFacts;

    /**
     * Creates the transactions.
     *
     * @param views Planning's read side
     * @param ordersForDate the date's orders, for each stop's location, weight and volume
     * @param stopOrders each stop's address, phones and notes, as a driver sees them, read for all
     *     the route's orders in one call (FR-86, FR-87)
     * @param fleetFacts Fleet's register, for the depots' locations
     */
    public RouteQueriesChange(
            PlanningViews views, OrdersForDate ordersForDate, StopOrders stopOrders, FleetFacts fleetFacts) {
        this.views = views;
        this.ordersForDate = ordersForDate;
        this.stopOrders = stopOrders;
        this.fleetFacts = fleetFacts;
    }

    /**
     * The points the route's line runs through, from its start depot through its stops to its end
     * depot, for the road network before the line is drawn (FR-24).
     *
     * <p>Built in M3.
     *
     * @param actor the dispatcher, or the route's driver for a plan in force (BR-02, BR-41)
     * @param route the route
     * @return the route, its index for its colour and label (FR-148), and its points in driving order
     * @throws NotFound when no plan holds the route, or the actor may not see it
     */
    @Transactional(readOnly = true)
    public Waypoints waypoints(Actor actor, RouteId route) {
        throw new UnsupportedOperationException("Built in M3");
    }

    /**
     * The route to print or export (FR-72).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param actor the dispatcher, or the route's driver for a plan in force (BR-02, BR-41)
     * @param route the route
     * @return its stops in order, with their addresses, phones, notes, weight and volume
     * @throws NotFound when no plan holds the route, or the actor may not see it
     */
    public PrintableRouteView printable(Actor actor, RouteId route) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }

    /**
     * The route's loading list, the last stop first (FR-153).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param actor the dispatcher, or the route's driver for a plan in force (BR-02, BR-41)
     * @param route the route
     * @return its orders in reverse stop order, with their weight and volume
     * @throws NotFound when no plan holds the route, or the actor may not see it
     */
    public LoadingListView loadingList(Actor actor, RouteId route) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }

    /**
     * What changed on the route since a revision the driver saw (FR-249, FR-97).
     *
     * <p>Built in M10.
     *
     * @param actor the dispatcher, or the route's driver (BR-02, BR-41)
     * @param route the route
     * @param revision the revision compared from, 1 or more
     * @return the stops added, removed and reordered since then
     * @throws NotFound when no published plan holds the route at that revision, or the actor may not
     *     see it
     */
    public RouteChangesView changesSince(Actor actor, RouteId route, int revision) {
        throw new UnsupportedOperationException("Built in M10");
    }

    /**
     * A route's points, which the road network's line runs through. They are customers' places, so
     * they print redacted.
     *
     * @param route the route
     * @param index the route's index, for its colour and label (FR-148)
     * @param points the start depot, the stops and the end depot, in driving order
     */
    public record Waypoints(RouteId route, RouteIndex index, List<GeoPoint> points) implements PersonalData {

        /** Checks every part is present, and copies the points. */
        public Waypoints {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(index, "index");
            points = List.copyOf(points);
        }

        /**
         * A form that hides the points.
         *
         * @return {@code Waypoints[redacted]}
         */
        @Override
        public String toString() {
            return "Waypoints[redacted]";
        }
    }
}
