package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.CancelledOrders;
import com.dispatchflow.reporting.domain.PlanStanding;
import com.dispatchflow.reporting.domain.PlannedFigures;
import com.dispatchflow.reporting.domain.StopResult;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import java.util.List;
import java.util.Optional;

/**
 * Reporting's one read port over other modules: every fact it reads from Planning, Live Operations
 * and Customers &amp; Orders comes through here, from the read-only views those modules publish
 * (DL §2.3 rule 3, ADR-041, ADR-109). Reporting calls no other module's Java API for them. Only the
 * adapter behind this port imports those views, and no SQL behind it reads another module's
 * tables.
 *
 * <p>Each answer is a record of this package or one of Reporting's own domain values, built from
 * kernel and JDK types, because Reporting models each fact it reads as its own value (DL §2.3 rule
 * 1, ADR-109). A day has at most 200 orders (BR-101), so each answer is one pass over at most 200
 * rows.
 *
 * <p>Built in M5 and an unscheduled milestone, method by method.
 */
public interface ReportingViews {

    /**
     * The plan of a date that the summary is about: the one published or completed, from
     * Planning's published view (ADR-041, ADR-109). A cancelled plan has no summary (FR-338).
     *
     * <p>Built in M5.
     *
     * @param date the date
     * @return the plan, or empty when no plan of the date is published or completed
     */
    Optional<PlanId> planOn(DeliveryDate date);

    /**
     * Where a published plan is now, from Planning's published view (FR-338, DL §9.1, ADR-041,
     * ADR-109).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return published, completed or cancelled; empty when no published plan has the ID
     */
    Optional<PlanStanding> standingOf(PlanId plan);

    /**
     * A published plan's planned figures, from its current revision (FR-38, FR-39, FR-266, FR-341,
     * ADR-041, ADR-109): its distance, its driver-hours and each order of its day with what the plan
     * does with it, from Planning's published view, and each order's window, from Customers &amp;
     * Orders' published view. The orders cancelled now are in it; {@link #cancelledOrdersOf} names
     * them, and the rates leave them out (BR-30).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the figures, or empty when no published plan has the ID
     */
    Optional<PlannedFigures> plannedFiguresOf(PlanId plan);

    /**
     * What happened at each stop of a plan's latest revision (FR-38, FR-264, FR-333, FR-340,
     * FR-370, ADR-041, ADR-109): the stops, from Planning's published view; each one's arrival and
     * effective outcome with its time, as corrected for the device's clock (BR-114), from Live
     * Operations' published view, a held report counting as no outcome (BR-132); and each order's
     * window and door flag, from Customers &amp; Orders' published view. A stop of an order
     * cancelled now is in it, and {@code OnTimeRate} leaves it out (BR-30).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return one result per stop, each once; empty for a plan with no stop or no published plan
     *     with the ID
     */
    List<StopResult> stopResultsOf(PlanId plan);

    /**
     * The orders of a plan's date that are cancelled now, from Customers &amp; Orders' published
     * view (BR-30, ADR-041, ADR-109).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the cancelled orders, each once; none when no published plan has the ID
     */
    CancelledOrders cancelledOrdersOf(PlanId plan);

    /**
     * Every route of a plan as its latest revision has it, whether the plan is published,
     * completed or cancelled, from Planning's published view (FR-38, FR-106, ADR-041, ADR-109): the
     * per-route lines of the daily summary and the planned arrivals.
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the routes, in the plan's order; empty when no published plan has the ID
     */
    List<PlannedRoute> routesOf(PlanId plan);

    /**
     * What a published plan's baseline is solved from (FR-39, FR-271, FR-342, ADR-052, ADR-041,
     * ADR-109): the orders of its current revision and the fleet and depots its daily solve was
     * given, from Planning's published view, and each order's location, window, demand, service
     * time, priority and door flag, from Customers &amp; Orders' published view, leaving out the
     * orders cancelled now. The matrix and the travel values are not in it: M5 decides how the
     * baseline gets them (see {@link BaselineRequest}).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the inputs, or empty when no published plan has the ID
     */
    Optional<BaselineInputs> baselineInputsOf(PlanId plan);

    /**
     * What a plan's vehicles actually drove, from Live Operations' published view (FR-106,
     * ADR-041, ADR-109). Q-40 settled that actuals come from the apps' report times and the
     * position traces (NFR-249); no milestone measures the distance and driving time from the traces
     * yet. The actual arrivals come from {@link #stopResultsOf}, in M5.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param plan the plan
     * @return the distance and the time driven, each if measured
     */
    ActualFigures actualsOf(PlanId plan);

    /**
     * Every failed stop and dropped order of the published or completed plans of a range of dates
     * (FR-161, ADR-041, ADR-109): the plans, their drops and their routes' drivers, from Planning's
     * published view; each stop's effective outcome and failure reason, from Live Operations'; and
     * each order's customer, the customer's name and the order's cancellation, from Customers &amp;
     * Orders'. The order's effective
     * outcome counts (FR-333), and a cancelled order is left out (BR-30).
     *
     * <p>Built in an unscheduled milestone: FR-161, a Should, is in no milestone of the roadmap.
     *
     * @param from the first date, included
     * @param to the last date, included; a range with {@code to} before {@code from} has none
     * @return the failures and drops, by date and then by order
     */
    List<FailureOrDrop> failuresAndDrops(DeliveryDate from, DeliveryDate to);
}
