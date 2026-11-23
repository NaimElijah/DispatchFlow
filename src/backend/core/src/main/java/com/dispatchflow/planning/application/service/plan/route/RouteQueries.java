package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.RouteChangesView;
import com.dispatchflow.planning.application.port.out.plan.LoadingListView;
import com.dispatchflow.planning.application.port.out.plan.PrintableRouteView;
import com.dispatchflow.planning.application.port.out.plan.RouteShapeView;
import com.dispatchflow.planning.application.port.out.plan.RouteShapes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.NotFound;

/**
 * One route's screens: its line on the map, its printout, its loading list, and what a route change
 * added, removed or reordered (FR-24, FR-72, FR-153, FR-249). It joins the stops' locations, addresses,
 * phones, notes and demands from Customers &amp; Orders, and the depots' locations from Fleet, because
 * Planning keeps none of them.
 *
 * <p>Data scope: a driver sees only a route assigned to them, of a plan in force, never one of a
 * draft, solving, ready or abandoned plan, whatever the account's other roles (BR-02, BR-41). A
 * dispatcher sees every route. This class enforces it once its milestone builds it; the handler's role
 * check from {@code x-capability} can't, because it depends on the data (ADR-068).
 *
 * <p>Built in M3 and later milestones, method by method.
 */
public final class RouteQueries {

    private final PlanningViews views;
    private final RouteShapes routeShapes;
    private final OrdersForDate ordersForDate;
    private final StopOrders stopOrders;
    private final FleetFacts fleetFacts;

    /**
     * Creates the queries.
     *
     * @param views Planning's read side
     * @param routeShapes the road network's line through a route's points
     * @param ordersForDate the date's orders, for each stop's location, weight and volume
     * @param stopOrders each stop's address, phones and notes, as a driver sees them, read for all
     *     the route's orders in one call (FR-86, FR-87)
     * @param fleetFacts Fleet's register, for the depots' locations
     */
    public RouteQueries(
            PlanningViews views,
            RouteShapes routeShapes,
            OrdersForDate ordersForDate,
            StopOrders stopOrders,
            FleetFacts fleetFacts) {
        this.views = views;
        this.routeShapes = routeShapes;
        this.ordersForDate = ordersForDate;
        this.stopOrders = stopOrders;
        this.fleetFacts = fleetFacts;
    }

    /**
     * The route's line along the road network, from its start depot through its stops to its end
     * depot (FR-24).
     *
     * <p>Built in M3.
     *
     * @param actor the dispatcher, or the route's driver for a plan in force (BR-02, BR-41)
     * @param route the route
     * @return the line, with the route's index for its colour and label (FR-148)
     * @throws NotFound when no plan holds the route, or the actor may not see it
     * @throws com.dispatchflow.planning.application.port.out.RoadNetworkUnavailable when the road
     *     network can't be reached
     */
    public RouteShapeView shape(Actor actor, RouteId route) {
        throw new UnsupportedOperationException("Built in M3");
    }

    /**
     * The route to print or export (FR-72). It reads every stop's details with one {@code
     * StopOrders.of} call, which takes the route's orders as a list, each once, and answers them in
     * the order asked, so the printout keeps the route's order.
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
}
