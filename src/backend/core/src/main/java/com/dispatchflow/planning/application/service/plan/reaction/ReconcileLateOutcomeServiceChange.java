package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.api.LateOutcomeFact;
import com.dispatchflow.planning.application.api.ReconciliationFact;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link ReconcileLateOutcomeService}: it finds the plan that holds or held the
 * stop, reconciles the late fact with {@code Plan.reconcileLateOutcome}, saves the plan and writes the
 * audit record (DL §8, TS §6.2, ADR-063, ADR-101, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class ReconcileLateOutcomeServiceChange {

    private final PlanRepository plans;
    private final PlanningViews views;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept
     * @param views Planning's read side, for the plan that holds or held the stop
     * @param clock the time, for {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public ReconcileLateOutcomeServiceChange(
            PlanRepository plans, PlanningViews views, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.views = views;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reconciles the late fact with the plan that holds or held its stop.
     *
     * <p>To Be Implemented.
     *
     * @param fact the late outcome, as Live Operations recorded it
     * @param call Live Operations' listener step, which the audit record names (ADR-110)
     * @return what reconciling did to the plan, and the plan, for the dispatchers' open screens
     * @throws StaleVersion when the plan changed between its read and its save; Live Operations'
     *     listener adapter retries at once, up to three times (DL §11)
     */
    public Reconciled apply(LateOutcomeFact fact, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * What reconciling did, with the plan it reconciled, which the screens are told of after the
     * commit.
     *
     * @param fact what reconciling did to the plan, the answer Live Operations gets
     * @param plan the plan that holds or held the stop
     */
    public record Reconciled(ReconciliationFact fact, PlanId plan) {

        /** Checks every part is present. */
        public Reconciled {
            Objects.requireNonNull(fact, "fact");
            Objects.requireNonNull(plan, "plan");
        }
    }
}
