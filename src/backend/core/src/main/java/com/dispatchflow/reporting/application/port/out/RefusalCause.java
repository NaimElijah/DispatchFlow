package com.dispatchflow.reporting.application.port.out;

/**
 * Why the optimizer found no baseline, in Reporting's own words (FR-39, FR-342, ADR-004, BR-15).
 * {@link BaselinePlanner}'s adapter maps the optimizer's answer into it, in the anti-corruption
 * layer: a {@code Failed} outcome's closed kind, {@code SOLVER_FAILED} or {@code
 * NO_SOLUTION_WITHIN_BUDGET}, one to one, and a {@code Rejected} outcome as {@link
 * #VALIDATOR_REJECTED}. So no free text from the optimizer reaches Reporting. A kind it doesn't know
 * is no cause: the adapter treats it like an optimizer that can't be reached.
 *
 * <p>To Be Implemented.
 */
public enum RefusalCause {

    /** The optimizer's solver failed. */
    SOLVER_FAILED,

    /** The solver found no solution within its budget. */
    NO_SOLUTION_WITHIN_BUDGET,

    /** The optimizer's validator rejected the solution (BR-15). */
    VALIDATOR_REJECTED
}
