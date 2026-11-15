package com.dispatchflow.planning.events;

/** Why a plan's revision was made, which every revision records (BR-77, FR-104, ADR-059). */
public enum RevisionCause {
    /** Publishing made revision 1 (BR-12). */
    PUBLISHED,
    /** A dispatcher accepted a re-optimization (BR-62). */
    REOPTIMIZATION_ACCEPTED,
    /** A route moved to another driver (FR-55). */
    HANDED_OVER,
    /** An order was cancelled, and its stop left the plan (FR-207). */
    ORDER_CANCELLED,
    /** An order moved to another date, and its stop left the plan (FR-315). */
    ORDER_RESCHEDULED,
    /** A failed stop was corrected to delivered after a put-back, and the new stop left (FR-404). */
    CORRECTED_AFTER_PUT_BACK,
    /** A late fact put its stop on the route that served it (BR-89, BR-130). */
    LATE_OUTCOME
}
