package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.events.OrderPutBack;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RemovePutBackOrder}: it loads the old date's uncancelled plan, takes the
 * put-back attempt's drop off with {@code Plan.removePutBackOrder}, saves the plan and writes the
 * audit record (TS §6.2, ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class RemovePutBackOrderChange {

    private final PlanRepository plans;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept, and each date's uncancelled plan
     * @param clock the time, for the audit record
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RemovePutBackOrderChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Takes the put-back attempt's drop off its old date's plan. A put-back onto the same date changes
     * no plan (DL §6.2).
     *
     * <p>To Be Implemented.
     *
     * @param event the put-back
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @return the plan it changed, for the dispatchers' open screens, or empty when it changed none
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public Optional<PlanId> apply(OrderPutBack event, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
