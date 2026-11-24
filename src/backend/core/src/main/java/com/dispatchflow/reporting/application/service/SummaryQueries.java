package com.dispatchflow.reporting.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.reporting.application.port.out.BaselineView;
import com.dispatchflow.reporting.application.port.out.DailySummaryView;
import com.dispatchflow.reporting.application.port.out.FailuresAndDropsView;
import com.dispatchflow.reporting.application.port.out.PlannedVsActualView;
import com.dispatchflow.reporting.application.port.out.ReportingViews;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.NotFound;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily summary's and the baseline comparison's reads (FR-38, FR-39, FR-106, FR-161). Every
 * figure is computed when it is asked for, from the facts read now, so a correction made later
 * changes the next figure (FR-333, ADR-109):
 *
 * <ul>
 *   <li>{@link ReportingViews} gives every fact of another module, from the views Planning, Live
 *       Operations and Customers &amp; Orders publish (ADR-041): a plan's standing, planned
 *       figures, routes with their planned arrivals, stop results, actual figures, and the orders
 *       cancelled now (BR-30). The class calls no other module's Java API for them;
 *   <li>{@link BaselineRepository} gives the plan's baseline;
 *   <li>Reporting's own domain judges them: {@code OnTimeRate}, {@code PlannedOnTimeRate} through
 *       {@code PlannedFigures}, and {@code Baseline.compareWith}.
 * </ul>
 *
 * <p>Every read is the dispatcher's, authorized by role from its operation (ADR-068); no driver
 * scope applies (BR-02), so no check depends on the data.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class SummaryQueries {

    private final ReportingViews views;
    private final BaselineRepository baselines;

    /**
     * Creates the queries.
     *
     * @param views Reporting's read port over the views other modules publish
     * @param baselines where baselines are kept
     */
    public SummaryQueries(ReportingViews views, BaselineRepository baselines) {
        this.views = views;
        this.baselines = baselines;
    }

    /**
     * A date's daily summary, per route and in total (FR-38, FR-264, FR-265, FR-338 to FR-341,
     * FR-370, BR-129): the planned distance, driving time and driver-hours, the stops by outcome,
     * the on-time percentage, the vehicles used and the dropped orders. Neither count includes a
     * cancelled order (BR-30).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the summary, empty when the date has no published or completed plan
     */
    public DailySummaryView daily(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * A plan set against its baseline (FR-39, FR-266, FR-269, FR-270): {@code Baseline.compareWith}
     * with the plan's standing, its current figures and the orders cancelled now.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @return the comparison, empty while the baseline is not computed yet
     * @throws NotFound when Planning's view has no published plan with the ID
     */
    public BaselineView baseline(Actor actor, PlanId plan) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * A date's planned distance, driving time and arrivals beside the actual ones (FR-106). The
     * actual arrivals come from {@link ReportingViews#stopResultsOf}; the actual distance and
     * driving time from {@link ReportingViews#actualsOf}, which is not planned yet, so until it is
     * built both are empty (Q-40, NFR-249).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the comparison, empty when the date has no published or completed plan
     */
    public PlannedVsActualView plannedVsActual(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The failed and dropped orders of a range of dates, for the screen to group by reason,
     * customer and driver (FR-161).
     *
     * <p>The web adapter bounds the range at the boundary, refusing one longer than a named
     * maximum, so one call never reads an unbounded span of dates; the bound is set when FR-161 is
     * planned.
     *
     * <p>To Be Implemented. FR-161 is a Should.
     *
     * @param actor the dispatcher
     * @param from the first date, included
     * @param to the last date, included; a range with {@code to} before {@code from} has none
     * @return the failures and drops of the range
     */
    public FailuresAndDropsView failuresAndDrops(Actor actor, DeliveryDate from, DeliveryDate to) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
