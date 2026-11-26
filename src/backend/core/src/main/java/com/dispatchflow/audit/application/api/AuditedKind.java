package com.dispatchflow.audit.application.api;

/**
 * What kind of thing an audited operation names (NFR-93): one constant per typed ID of the shared
 * kernel (DL §2.3 rule 5, ADR-110). A driver shares its account's ID, so it is named as an {@link
 * #ACCOUNT}. Audit's domain keeps its own copy, {@code RecordKind}.
 */
public enum AuditedKind {
    /** A person's account, named by an {@code AccountId}. */
    ACCOUNT,
    /** A signed-in device, named by a {@code SessionId}. */
    SESSION,
    /** A depot, named by a {@code DepotId}. */
    DEPOT,
    /** A vehicle, named by a {@code VehicleId}. */
    VEHICLE,
    /** A customer, named by a {@code CustomerId}. */
    CUSTOMER,
    /** An order, named by an {@code OrderId}. */
    ORDER,
    /** An import, named by an {@code ImportBatchId}. */
    IMPORT_BATCH,
    /** A plan, named by a {@code PlanId}. */
    PLAN,
    /** A route, named by a {@code RouteId}. */
    ROUTE,
    /** A stop, named by a {@code StopId}. */
    STOP,
    /** A solve job, named by a {@code JobId}. */
    JOB,
    /** A re-optimization proposal, named by a {@code ProposalId}. */
    PROPOSAL,
    /** An alert, named by an {@code AlertId}. */
    ALERT,
    /** A rating request, named by a {@code RatingRequestId}. */
    RATING_REQUEST
}
