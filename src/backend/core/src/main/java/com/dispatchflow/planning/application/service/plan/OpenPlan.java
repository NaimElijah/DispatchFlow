package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * Opens a plan for a delivery date, in draft (FR-15, BR-34, BR-118). It reads the date's uncancelled
 * plan first, and the partial unique index on the date refuses a race the read missed (DL §5).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M0.
 */
public final class OpenPlan {

    private final PlanRepository plans;
    private final Clock clock;
    private final IdGenerator ids;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param clock the time, for {@code today} and {@code now}
     * @param ids the new plan's ID
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public OpenPlan(PlanRepository plans, Clock clock, IdGenerator ids, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.ids = ids;
        this.liveUpdates = liveUpdates;
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
    public PlanId open(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
