package com.dispatchflow.reporting.domain;

/**
 * Where a published plan is now, as Planning's view gives it: the states a plan with a baseline can
 * be in (DL §9.1). Reporting models it itself (DL §2.3, rule 1).
 */
public enum PlanStanding {
    /** The plan is the drivers' plan for its date. */
    PUBLISHED,
    /** The plan's date is over (BR-102). */
    COMPLETED,
    /** The plan was cancelled, so it has no baseline comparison (FR-338). */
    CANCELLED
}
