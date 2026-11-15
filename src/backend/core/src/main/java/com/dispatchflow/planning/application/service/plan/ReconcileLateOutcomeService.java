package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.api.LateOutcomeFact;
import com.dispatchflow.planning.application.api.ReconcileLateOutcome;
import com.dispatchflow.planning.application.api.ReconciliationFact;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Planning's implementation of {@link ReconcileLateOutcome}: it finds the plan that holds or held the
 * stop, translates the fact into Planning's {@code LateFact}, and calls {@code
 * Plan.reconcileLateOutcome}, which makes a new revision when the stop moves (DL §8, BR-89, BR-130,
 * ADR-101). It doesn't retry a refused save: Live Operations' listener adapter retries the whole
 * call, so the answer is the plan's as it then is (DL §11).
 *
 * <p>Built in M8.
 */
public final class ReconcileLateOutcomeService implements ReconcileLateOutcome {

    private final PlanRepository plans;
    private final PlanningViews views;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the service.
     *
     * @param plans where plans are kept
     * @param views Planning's read side, for the plan that holds or held the stop
     * @param clock the time, for {@code now}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public ReconcileLateOutcomeService(
            PlanRepository plans, PlanningViews views, Clock clock, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.plans = plans;
        this.views = views;
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
    public ReconciliationFact reconcile(LateOutcomeFact fact, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
