package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link OpenPlan}: it reads the date's uncancelled plan, opens the new one in
 * draft, adds it and writes the audit record of its success (DL §5, TS §6.2, ADR-063, ADR-129).
 *
 * <p>An opening that races past the read is refused by the add itself: the port takes the date's
 * lock and answers {@code PlanAlreadyExists}, naming the plan stored first, and the partial unique
 * index on the date is the backstop (BR-34, ADR-127 decision 9). So the race is a refusal like the
 * read's, with no {@code StaleVersion} and no retry.
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
     * @param actor the dispatcher
     * @param date the delivery date
     * @return the new plan's ID, with the version its add stored
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code DateInPast} for a past date
     *     (BR-118), or {@code PlanAlreadyExists} when the date has an uncancelled plan, found by the
     *     read or by the add's lock (BR-34)
     */
    public OpenedPlan apply(Actor actor, DeliveryDate date) {
        Instant now = clock.now();
        Plan plan = Plan.open(PlanId.of(ids.next()), date, plans.plansOn(date), clock.today(), now);
        plans.add(plan);
        auditLog.succeeded(actor.call(), now, List.of(AuditedRecord.of(plan.id())));
        return new OpenedPlan(plan.id(), plan.version());
    }
}
