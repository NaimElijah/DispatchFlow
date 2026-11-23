package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.events.OrderCancelled;
import com.dispatchflow.orders.events.OrderRescheduled;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link RemoveStopOfOrder}, one for each event: each loads the date's
 * uncancelled plan, takes the order's stop off with {@code Plan.removeStopOf}, saves the plan and
 * writes the audit record (TS §6.2, ADR-063, ADR-129).
 *
 * <p>Built in M9.
 */
@Transactional
public class RemoveStopOfOrderChange {

    private final PlanRepository plans;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param plans where plans are kept, and each date's uncancelled plan
     * @param clock the time, for {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RemoveStopOfOrderChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
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
     * @return the plan it changed, for the dispatchers' open screens, or empty when it changed none
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public Optional<PlanId> apply(OrderCancelled event, CallContext call) {
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
     * @return the plan it changed, for the dispatchers' open screens, or empty when it changed none
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public Optional<PlanId> apply(OrderRescheduled event, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
