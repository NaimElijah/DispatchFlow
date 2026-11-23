package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;

/**
 * Opens a plan for a delivery date, in draft (FR-15, BR-34, BR-118). It reads the date's uncancelled
 * plan first, and the partial unique index on the date refuses a race the read missed (DL §5).
 *
 * <p>{@link OpenPlanChange} holds the transaction, which adds the plan and writes the audit record;
 * after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M0.
 */
public final class OpenPlan {

    private final LiveUpdates liveUpdates;
    private final OpenPlanChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the opening's transaction
     */
    public OpenPlan(LiveUpdates liveUpdates, OpenPlanChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
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
