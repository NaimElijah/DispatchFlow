package com.dispatchflow.planning.application.api;

/**
 * Where a published plan is now: the states a plan with a baseline can be in (DL §9.1, FR-338).
 * Reporting translates it into its {@code PlanStanding}.
 *
 * <p>Built in M5.
 */
public enum PlanStandingFact {
    /** The plan is the drivers' plan for its date. */
    PUBLISHED,
    /** The plan's date is over (BR-102). */
    COMPLETED,
    /** The plan was cancelled, so it has no baseline comparison (FR-338). */
    CANCELLED
}
