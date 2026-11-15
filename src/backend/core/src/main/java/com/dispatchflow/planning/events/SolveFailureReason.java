package com.dispatchflow.planning.events;

/** Why a solve job failed, which the dispatcher is told (FR-65, BR-86, DL §9.2). */
public enum SolveFailureReason {
    /** It waited in its queue past 5 minutes (BR-112). */
    QUEUED_TOO_LONG,
    /** Building its travel-time matrix passed its own deadline (ADR-101). */
    MATRIX_TOO_LONG,
    /** Its search ran 30 seconds past its limit (BR-32, BR-84). */
    OVERRAN,
    /** The optimization service could not be reached (FR-65). */
    OPTIMIZER_UNAVAILABLE,
    /** The road-routing service could not build the matrix (FR-65). */
    ROAD_NETWORK_UNAVAILABLE,
    /** A restart interrupted it, or found a re-optimization queued (BR-74, BR-85). */
    INTERRUPTED,
    /** Its date passed while it was queued or running (BR-128). */
    DATE_PASSED,
    /** Its date had no uncancelled orders (FR-65). */
    NO_ORDERS,
    /**
     * The validator rejected the solution (BR-57), or Planning found one it accepted that no plan
     * could hold, with an order twice (BR-07) or a vehicle or driver on two routes (BR-39) (ADR-112).
     */
    VALIDATOR_REJECTED,
    /** A locked route could not be kept (FR-238). */
    LOCK_NOT_HONOURED,
    /** The optimizer's solver failed during its search (FR-65, ADR-121). */
    SOLVER_FAILED,
    /** The optimizer found no solution within the job's limit (FR-65, ADR-121). */
    NO_SOLUTION_WITHIN_BUDGET,
    /**
     * The optimizer refused the request: an unknown solver, an unsupported budget or an invalid
     * problem. It is a fault for the team, not the dispatcher's (FR-65, ADR-121).
     */
    REQUEST_REFUSED
}
