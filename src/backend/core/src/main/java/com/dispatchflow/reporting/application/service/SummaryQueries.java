package com.dispatchflow.reporting.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.api.StopResults;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.api.PlanFigures;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.reporting.application.port.out.BaselineView;
import com.dispatchflow.reporting.application.port.out.DailySummaryView;
import com.dispatchflow.reporting.application.port.out.FailuresAndDropsView;
import com.dispatchflow.reporting.application.port.out.PlannedVsActualView;
import com.dispatchflow.reporting.application.port.out.ReportingViews;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.NotFound;

/**
 * The daily summary's and the baseline comparison's reads (FR-38, FR-39, FR-106, FR-161). Every
 * figure is computed when it is asked for, from each module's facts read now, so a correction
 * made later changes the next figure (FR-333, ADR-109):
 *
 * <ul>
 *   <li>Planning's {@link PlanFigures} gives a plan's planned figures and standing, and {@link
 *       PublishedRoutes} its routes, vehicles, drivers and planned arrivals;
 *   <li>Live Operations' {@link StopResults} gives each stop's arrival and effective outcome;
 *   <li>Customers &amp; Orders' {@link OrdersForDate} gives each order's window and door flag, and
 *       the orders cancelled now (BR-30);
 *   <li>Reporting's own domain judges them: {@code OnTimeRate}, {@code PlannedOnTimeRate} through
 *       {@code PlannedFigures}, and {@code Baseline.compareWith}.
 * </ul>
 *
 * <p>Every read is the dispatcher's, authorized by role from its operation (ADR-068); no driver
 * scope applies (BR-02), so no check depends on the data.
 *
 * <p>Built in M5.
 */
public final class SummaryQueries {

    private final ReportingViews views;
    private final BaselineRepository baselines;
    private final PlanFigures planFigures;
    private final PublishedRoutes publishedRoutes;
    private final StopResults stopResults;
    private final OrdersForDate ordersForDate;

    /**
     * Creates the queries.
     *
     * @param views Reporting's read port over the other modules' published views
     * @param baselines where baselines are kept
     * @param planFigures Planning's figures and standing of a published plan
     * @param publishedRoutes Planning's routes of the plans in force
     * @param stopResults Live Operations' arrivals and effective outcomes of a plan's stops
     * @param ordersForDate Customers &amp; Orders' orders of a date
     */
    public SummaryQueries(
            ReportingViews views,
            BaselineRepository baselines,
            PlanFigures planFigures,
            PublishedRoutes publishedRoutes,
            StopResults stopResults,
            OrdersForDate ordersForDate) {
        this.views = views;
        this.baselines = baselines;
        this.planFigures = planFigures;
        this.publishedRoutes = publishedRoutes;
        this.stopResults = stopResults;
        this.ordersForDate = ordersForDate;
    }

    /**
     * A date's daily summary, per route and in total (FR-38, FR-264, FR-265, FR-338 to FR-341,
     * FR-370, BR-129): the planned distance and driver-hours, the stops by outcome, the on-time
     * percentage, the vehicles used and the dropped orders. Neither count includes a cancelled
     * order (BR-30).
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the summary, empty when the date has no published or completed plan
     */
    public DailySummaryView daily(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * A plan set against its baseline (FR-39, FR-266, FR-269, FR-270): {@code Baseline.compareWith}
     * with the plan's standing, its current figures and the orders cancelled now.
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @return the comparison, empty while the baseline is not computed yet
     * @throws NotFound when no published plan has the ID
     */
    public BaselineView baseline(Actor actor, PlanId plan) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * A date's planned distance, driving time and arrivals beside the actual ones (FR-106).
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the comparison, empty when the date has no published or completed plan
     */
    public PlannedVsActualView plannedVsActual(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * The failed and dropped orders of a range of dates, for the screen to group by reason,
     * customer and driver (FR-161).
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher
     * @param from the first date, included
     * @param to the last date, included; a range with {@code to} before {@code from} has none
     * @return the failures and drops of the range
     */
    public FailuresAndDropsView failuresAndDrops(Actor actor, DeliveryDate from, DeliveryDate to) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
