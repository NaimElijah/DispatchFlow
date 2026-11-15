package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.api.RemoveSupersededPutBack;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Planning's implementation of {@link RemoveSupersededPutBack}: it loads the date's uncancelled plan
 * and calls {@code Plan.removeStopOf} with the cause {@code CORRECTED_AFTER_PUT_BACK}, which changes
 * nothing when the stop is already gone (FR-404, ADR-114).
 *
 * <p>Built in M8.
 */
public final class RemoveSupersededPutBackService implements RemoveSupersededPutBack {

    private final PlanRepository plans;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the service.
     *
     * @param plans where plans are kept, and each date's uncancelled plan
     * @param clock the time, for {@code now}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RemoveSupersededPutBackService(
            PlanRepository plans, Clock clock, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M8.
     *
     * @throws StaleVersion when the plan changed between its read and its save; Live Operations'
     *     listener adapter retries at once, up to three times (DL §11)
     */
    @Override
    public void remove(OrderId order, Attempt laterAttempt, DeliveryDate date, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
