package com.dispatchflow.reporting.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.api.PlanFigures;
import com.dispatchflow.planning.events.PlanPublished;
import com.dispatchflow.reporting.application.port.out.BaselinePlanner;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;

/**
 * Computes a published plan's baseline when Reporting hears {@code PlanPublished} (FR-39, FR-342,
 * DL §7.2). Before its transaction it builds the {@code BaselineRequest} from Planning's {@link
 * PlanFigures#inputsOf} and Customers &amp; Orders' {@link OrdersForDate#uncancelledOn}, and asks
 * the {@link BaselinePlanner} for the nearest-neighbour solve (DL §6.3); then {@code
 * Baseline.compute} stores it. Delivery is at least once (ADR-064): the plan keys the baseline, so
 * a repeated delivery finds it stored and changes nothing.
 *
 * <p>Built in M5.
 */
public final class ComputeBaseline {

    private final PlanFigures planFigures;
    private final OrdersForDate ordersForDate;
    private final BaselinePlanner baselinePlanner;
    private final BaselineRepository baselines;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the listener's use case.
     *
     * @param planFigures Planning's figures and baseline inputs of a published plan
     * @param ordersForDate Customers &amp; Orders' orders of a date
     * @param baselinePlanner the optimizer's baseline solve
     * @param baselines where baselines are kept
     * @param clock the time of computing
     * @param auditLog the audit trail
     */
    public ComputeBaseline(
            PlanFigures planFigures,
            OrdersForDate ordersForDate,
            BaselinePlanner baselinePlanner,
            BaselineRepository baselines,
            Clock clock,
            AuditLog auditLog) {
        this.planFigures = planFigures;
        this.ordersForDate = ordersForDate;
        this.baselinePlanner = baselinePlanner;
        this.baselines = baselines;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Computes the baseline of the plan just published.
     *
     * <p>Built in M5.
     *
     * @param event the publication
     * @param call the listener's server step, which the audit record is written with
     * @throws NotFound when Planning has no published plan with the event's ID, which only a bug
     *     causes
     * @throws RuleViolation with Reporting's {@code NegativeDistance}, {@code NegativeDriverHours}
     *     or {@code OrderCountedTwice} when the optimizer's answer translates into no valid figures
     */
    public void on(PlanPublished event, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
