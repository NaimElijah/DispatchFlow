package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.DriverDayView;
import com.dispatchflow.liveops.application.port.out.DriverRoutesAheadView;
import com.dispatchflow.liveops.application.port.out.LiveOpsViews;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.application.Clock;

/**
 * The driver's day: their own routes of a date, the stops in order with projected arrivals, windows,
 * coordinates, customer details and notes (FR-27, FR-28, FR-96, FR-154, FR-324, FR-325, placement
 * P6). It picks the driver's routes from Planning's {@link PublishedRoutes}, whose stops carry the
 * windows and door flags their revision took them with, reads every stop's details from Customers
 * &amp; Orders' {@link StopOrders} in one call for all the day's orders, takes each stop's stored
 * coordinates from the order's {@code OrderFact.location} in {@link OrdersForDate#uncancelledOn}, so
 * Navigate honours a corrected pin (FR-325), and projects the arrivals itself; the route-change diff
 * (FR-249) stays Planning's. A stop whose order was cancelled after it was served is not among the
 * uncancelled orders, so it has no coordinates; it has an outcome, so it needs no Navigate.
 *
 * <p>It also lists the driver's routes from today on, each with its date (FR-324). Planning's {@link
 * PublishedRoutes} reads one date at a time, so that read needs a new method of it: M5 adds {@code
 * PublishedRoutes.inForceFrom(DeliveryDate)}, the routes of every plan in force from a date on.
 *
 * <p>A driver sees only their own routes (BR-02, BR-41): the routes are those whose current driver
 * is the acting account, whatever the request names.
 *
 * <p>Built in M5; the projected arrivals in M8. Rows: FR-27, FR-28, FR-96, FR-154, FR-324, FR-325,
 * BR-41.
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
     * @param publishedRoutes Planning's routes in force, with each stop's window and door flag
     * @param stopOrders Customers &amp; Orders' fields a driver sees at the stops
     * @param ordersForDate Customers &amp; Orders' orders of a date, for each stop's stored
     *     coordinates
     * @param clock the server's time, which the projections are made at, and today's date
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

    /**
     * The acting driver's routes from today on, each with its date, which the driver opens one date
     * at a time through {@link #myDay} (FR-324).
     *
     * <p>Built in M5.
     *
     * @param actor the driver
     * @return the routes, by date; none when the driver has no route in a plan in force from today
     */
    public DriverRoutesAheadView myRoutesAhead(Actor actor) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
