package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.events.OrderCancelled;
import com.dispatchflow.orders.events.OrderRescheduled;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Takes a cancelled or rescheduled order's stop off the plan of its date at once, without optimizing
 * (FR-207, FR-315, FR-316, DL §6.2). It overrides a lock. On a ready plan the journal is cleared; on a
 * published plan it is a revision with the cause, and raises {@code PlanRevised}. The stop's route is
 * then <em>not yet checked</em>, for the re-check job (FR-395).
 *
 * <p>It is idempotent by nature: {@code Plan.removeStopOf} changes nothing for an order the plan
 * doesn't hold, or in any other state (ADR-101). It writes its audit record with the listener's server step (ADR-110).
 *
 * <p>Built in M9.
 */
public final class RemoveStopOfOrder {

    private final PlanRepository plans;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept, and each date's uncancelled plan
     * @param clock the time, for {@code now}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RemoveStopOfOrder(PlanRepository plans, Clock clock, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Takes a cancelled order's stop off its date's plan, with the cause {@code ORDER_CANCELLED}
     * (FR-207).
     *
     * <p>Built in M9.
     *
     * @param event the cancellation
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void on(OrderCancelled event, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * Takes a rescheduled order's stop off its old date's plan, with the cause {@code
     * ORDER_RESCHEDULED} (FR-315). A reschedule within the same date changes no plan.
     *
     * <p>Built in M9.
     *
     * @param event the reschedule
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void on(OrderRescheduled event, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
