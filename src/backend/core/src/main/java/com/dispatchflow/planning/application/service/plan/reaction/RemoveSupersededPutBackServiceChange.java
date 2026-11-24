package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RemoveSupersededPutBackService}: it loads the date's uncancelled plan,
 * takes the superseded put-back's stop off with {@code Plan.removeStopOf} and the cause {@code
 * CORRECTED_AFTER_PUT_BACK}, saves the plan and writes the audit record (FR-404, TS §6.2, ADR-063,
 * ADR-114, ADR-129).
 *
 * <p>Built in M8.
 */
@Transactional
public class RemoveSupersededPutBackServiceChange {

    private final PlanRepository plans;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept, and each date's uncancelled plan
     * @param clock the time, for {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RemoveSupersededPutBackServiceChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Takes the later attempt's stop off the date's plan, if it is still there.
     *
     * <p>Built in M8.
     *
     * @param order the order
     * @param laterAttempt the attempt the put-back started, whose stop the correction supersedes
     * @param date the date of the plan that holds it
     * @param call Live Operations' listener step, which the audit record names (ADR-110)
     * @return the plan it changed, for the dispatchers' open screens, or empty when it changed none
     * @throws StaleVersion when the plan changed between its read and its save; Live Operations'
     *     listener adapter retries at once, up to three times (DL §11)
     */
    public Optional<PlanId> apply(OrderId order, Attempt laterAttempt, DeliveryDate date, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
