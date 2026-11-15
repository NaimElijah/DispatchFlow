package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.AtRiskView;
import com.dispatchflow.liveops.application.port.out.LiveMapView;
import com.dispatchflow.liveops.application.port.out.LiveOpsViews;
import com.dispatchflow.liveops.application.port.out.RouteProgressView;
import com.dispatchflow.liveops.application.port.out.StopHistoryView;
import com.dispatchflow.liveops.application.port.out.UnconfirmedView;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import java.time.Duration;
import java.util.List;

/**
 * The dispatcher's live screens: the map, each route's progress, the stops at risk, the unconfirmed
 * route changes and a stop's history (FR-33, FR-34, FR-74, FR-158, FR-250, FR-251, FR-253, FR-333,
 * FR-369, FR-397). It joins Planning's {@link PublishedRoutes}, whose stops carry their windows and
 * door flags, and Customers &amp; Orders' {@link OrdersForDate}, for the orders' locations and last
 * changes, with Live Operations' own rows from {@link LiveOpsViews}, and projects arrivals with the
 * domain's {@code ArrivalProjection} at the {@link Clock}'s now, never stored (DL §10.4).
 *
 * <p>Built in M5 and M8, method by method.
 */
public final class LiveQueries {

    /** How old a position may be before the map marks it stale, by the server's clock (FR-74). */
    public static final Duration POSITION_STALE_AFTER = Duration.ofMinutes(5);

    private final LiveOpsViews views;
    private final PublishedRoutes publishedRoutes;
    private final OrdersForDate ordersForDate;
    private final Clock clock;

    /**
     * Creates the queries.
     *
     * @param views Live Operations' read port
     * @param publishedRoutes Planning's routes in force
     * @param ordersForDate Customers &amp; Orders' orders of a date, for their locations and last
     *     changes
     * @param clock the server's time
     */
    public LiveQueries(LiveOpsViews views, PublishedRoutes publishedRoutes, OrdersForDate ordersForDate, Clock clock) {
        this.views = views;
        this.publishedRoutes = publishedRoutes;
        this.ordersForDate = ordersForDate;
        this.clock = clock;
    }

    /**
     * The live map of a date: each vehicle's latest position and its age, and each stop's status
     * (FR-33, FR-74, FR-397).
     *
     * <p>Built in M5; the positions in M8.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the map; with no routes when the date has no plan in force
     */
    public LiveMapView liveMap(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * Each route's progress on a date: stops done, delay, next stop, last report and its driver's
     * confirmation (FR-158, FR-250). It reads the date's routes, runs and stops in one pass, not one
     * route per call.
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return one entry per route of the date's plan in force, in the plan's order; empty when the
     *     date has no plan in force
     */
    public List<RouteProgressView> routeProgress(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * A date's stops at risk: projected later than their window end less 15 minutes, never a
     * leave-at-door stop (FR-34, FR-251, FR-253, FR-369).
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the stops at risk now
     */
    public AtRiskView atRisk(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * A date's routes whose last change waits for its driver's confirmation (FR-250).
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the unconfirmed routes
     */
    public UnconfirmedView unconfirmed(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * A stop's history: its arrival, its outcomes with every correction, and its held reports
     * (FR-333).
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher
     * @param stop the stop
     * @return the history
     * @throws NotFound when nothing is recorded for the stop
     */
    public StopHistoryView stopHistory(Actor actor, StopId stop) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
