package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.Refusals;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.application.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Opens a plan for a delivery date, in draft (FR-15, BR-34, BR-118). It reads the date's uncancelled
 * plan first, and the repository's add refuses a race the read missed, under the date's lock, with
 * the partial unique index on the date as the backstop (DL §5, ADR-127 decision 9).
 *
 * <p>{@link OpenPlanChange} holds the transaction, which adds the plan and writes the audit record
 * of its success; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>A refused opening rolls the change back, and the use case then records the refusal in a
 * transaction of its own, through {@link Refusals} (NFR-23, ADR-110, ADR-149, ADR-150). The record
 * names no plan, since none was made.
 */
public final class OpenPlan {

    private final LiveUpdates liveUpdates;
    private final OpenPlanChange change;
    private final AuditLog auditLog;
    private final Clock clock;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the opening's transaction
     * @param auditLog the audit trail, which a refused opening is recorded in
     * @param clock the time a refused opening is recorded at
     */
    public OpenPlan(LiveUpdates liveUpdates, OpenPlanChange change, AuditLog auditLog, Clock clock) {
        this.liveUpdates = liveUpdates;
        this.change = change;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Opens the plan.
     *
     * <p>It takes the date as the request gave it and makes the delivery date inside the recorded
     * attempt, so a year outside 1 to 9999 is refused and recorded as the rules' refusals are: made
     * in the controller, the refusal would leave no record, since the contract's handler records
     * only a refusal raised before the handler starts (ADR-150 decision 4).
     *
     * @param actor the dispatcher
     * @param date the delivery date, as the request gave it
     * @return the new plan's ID, with the version its add stored, for the answer's {@code ETag}
     * @throws com.dispatchflow.shared.RuleViolation with the kernel's {@code DateOutOfRange} for a
     *     year outside 1 to 9999, Planning's {@code DateInPast} for a past date (BR-118), or {@code
     *     PlanAlreadyExists} when the date has an uncancelled plan (BR-34), once its refused record is
     *     stored
     */
    public OpenedPlan open(Actor actor, LocalDate date) {
        OpenedPlan opened = Refusals.recordedBy(
                auditLog, actor.call(), clock.now(), List.of(), () -> change.apply(actor, DeliveryDate.of(date)));
        liveUpdates.planChanged(opened.plan());
        return opened;
    }
}
