package com.dispatchflow.reporting.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.api.PlanFigures;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.reporting.application.port.out.BaselinePlanner;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.ProcessedEvents;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Recomputes a plan's baseline when Reporting hears a {@code PlanRevised} that withdrew a vehicle
 * mid-route or added orders, so both sides cover the same orders and fleet (FR-39, FR-267 to
 * FR-271, ADR-109, ADR-112). A revision that did neither changes nothing. Each withdrawal's cause
 * is mapped one to one into Reporting's {@code WithdrawalCause}. Before its transaction it asks
 * {@code Baseline.withdrawnFleetAfter} which vehicles the solve leaves out and which it gives their
 * whole shift (FR-271), builds the {@code BaselineRequest} from Planning's {@link
 * PlanFigures#inputsOf} and Customers &amp; Orders' {@link OrdersForDate#uncancelledOn}, and asks
 * the {@link BaselinePlanner} for the solve (DL §6.3); then {@code Baseline.recompute} stores it.
 *
 * <p>Delivery is at least once (ADR-064), and a recompute is not idempotent by nature, because it
 * stores a new solve: the use case records the {@code (listener, event ID)} pair in {@link
 * ProcessedEvents} in its transaction, and a repeated delivery changes nothing.
 *
 * <p>Built in M9.
 */
public final class RecomputeBaseline {

    private final PlanFigures planFigures;
    private final OrdersForDate ordersForDate;
    private final BaselinePlanner baselinePlanner;
    private final BaselineRepository baselines;
    private final ProcessedEvents processedEvents;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the listener's use case.
     *
     * @param planFigures Planning's figures and baseline inputs of a published plan
     * @param ordersForDate Customers &amp; Orders' orders of a date
     * @param baselinePlanner the optimizer's baseline solve
     * @param baselines where baselines are kept
     * @param processedEvents the listeners' idempotency record
     * @param clock the time of computing
     * @param auditLog the audit trail
     */
    public RecomputeBaseline(
            PlanFigures planFigures,
            OrdersForDate ordersForDate,
            BaselinePlanner baselinePlanner,
            BaselineRepository baselines,
            ProcessedEvents processedEvents,
            Clock clock,
            AuditLog auditLog) {
        this.planFigures = planFigures;
        this.ordersForDate = ordersForDate;
        this.baselinePlanner = baselinePlanner;
        this.baselines = baselines;
        this.processedEvents = processedEvents;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Recomputes the baseline after a revision, if the revision withdrew a vehicle or added orders.
     *
     * <p>Built in M9.
     *
     * @param event the revision
     * @param call the listener's server step, which the audit record is written with
     * @throws NotFound when Planning has no published plan with the event's ID, or the plan has no
     *     baseline yet, so the delivery fails and is resubmitted after the publication's listener
     * @throws RuleViolation with Reporting's {@code NegativeDistance}, {@code NegativeDriverHours}
     *     or {@code OrderCountedTwice} when the optimizer's answer translates into no valid figures
     * @throws StaleVersion when another recompute of the plan was saved since the baseline was
     *     loaded, so the delivery fails and is resubmitted
     */
    public void on(PlanRevised event, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
