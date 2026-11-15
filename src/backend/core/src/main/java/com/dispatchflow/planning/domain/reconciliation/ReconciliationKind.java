package com.dispatchflow.planning.domain.reconciliation;

/** What reconciling a late fact did to the plan (DL §8's table, ADR-101). */
public enum ReconciliationKind {
    /** The stop was already on the route that served it. */
    UNCHANGED,
    /** The stop left another driver's route for the serving one (BR-89). */
    MOVED_FROM_ROUTE,
    /** The stop left the drops for the serving route. */
    FROM_DROPPED,
    /** The stop's order was cancelled; it joined the serving route as served after cancellation (BR-130). */
    AFTER_CANCELLATION,
    /** The plan is cancelled and stays as it is; Live Operations records the fact (ADR-101). */
    PLAN_CANCELLED
}
