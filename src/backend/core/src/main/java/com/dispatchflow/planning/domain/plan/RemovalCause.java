package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.events.RevisionCause;

/** Why an order's stop leaves the plan without a re-optimization (FR-207, FR-315, FR-316, FR-404). */
public enum RemovalCause {
    /** The order was cancelled (FR-207, FR-316). */
    ORDER_CANCELLED(RevisionCause.ORDER_CANCELLED),
    /** The order moved to another date (FR-315). */
    ORDER_RESCHEDULED(RevisionCause.ORDER_RESCHEDULED),
    /** A failed stop was corrected to delivered after a put-back, so the new stop leaves (FR-404). */
    CORRECTED_AFTER_PUT_BACK(RevisionCause.CORRECTED_AFTER_PUT_BACK);

    private final RevisionCause revisionCause;

    RemovalCause(RevisionCause revisionCause) {
        this.revisionCause = revisionCause;
    }

    /**
     * The cause the revision records, on a published plan (BR-77).
     *
     * @return the revision cause
     */
    public RevisionCause revisionCause() {
        return revisionCause;
    }
}
