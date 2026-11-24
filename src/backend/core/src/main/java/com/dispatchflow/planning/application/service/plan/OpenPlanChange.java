package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link OpenPlan}: it reads the date's uncancelled plan, opens the new one in
 * draft, adds it and writes the audit record (DL §5, TS §6.2, ADR-063, ADR-129).
 *
 * <p>Built in M0.
 */
@Transactional
public class OpenPlanChange {

    private final PlanRepository plans;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept
     * @param clock the time, for {@code today} and {@code now}
     * @param ids the new plan's ID
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public OpenPlanChange(PlanRepository plans, Clock clock, IdGenerator ids, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Opens the plan.
     *
     * <p>Built in M0.
     *
     * @param actor the dispatcher
     * @param date the delivery date
     * @return the new plan's ID
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code DateInPast} for a past date
     *     (BR-118), or {@code PlanAlreadyExists} when the date has an uncancelled plan (BR-34)
     */
    public PlanId apply(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
