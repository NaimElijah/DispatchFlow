package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.DriverDayView;
import com.dispatchflow.liveops.application.port.out.LiveOpsViews;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.application.Clock;

/**
 * The driver's day: their own routes of a date, the stops in order with projected arrivals, windows,
 * customer details and notes (FR-27, FR-96, FR-154, FR-324, placement P6). It picks the driver's
 * routes from Planning's {@link PublishedRoutes}, the stops' details from Customers &amp; Orders'
 * {@link StopOrders}, and projects the arrivals itself; the route-change diff (FR-249) stays
 * Planning's.
 *
 * <p>A driver sees only their own routes (BR-02, BR-41): the routes are those whose current driver
 * is the acting account, whatever the request names.
 *
 * <p>Built in M5; the projected arrivals in M8. Rows: FR-27, FR-96, FR-154, FR-324, BR-41.
 */
public final class DriverDayQueries {

    private final LiveOpsViews views;
    private final PublishedRoutes publishedRoutes;
    private final StopOrders stopOrders;
    private final OrdersForDate ordersForDate;
    private final Clock clock;

    /**
     * Creates the queries.
     *
     * @param views Live Operations' read port
     * @param publishedRoutes Planning's routes in force
     * @param stopOrders Customers &amp; Orders' fields a driver sees at a stop
     * @param ordersForDate Customers &amp; Orders' orders of a date, for windows and door flags
     * @param clock the server's time, which the projections are made at
     */
    public DriverDayQueries(
            LiveOpsViews views,
            PublishedRoutes publishedRoutes,
            StopOrders stopOrders,
            OrdersForDate ordersForDate,
            Clock clock) {
        this.views = views;
        this.publishedRoutes = publishedRoutes;
        this.stopOrders = stopOrders;
        this.ordersForDate = ordersForDate;
        this.clock = clock;
    }

    /**
     * The acting driver's routes of a date.
     *
     * <p>Built in M5; the projected arrivals in M8.
     *
     * @param actor the driver
     * @param date the date, today or a later one (FR-324)
     * @return the driver's routes; none when the date has no plan in force or the driver no route
     */
    public DriverDayView myDay(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
