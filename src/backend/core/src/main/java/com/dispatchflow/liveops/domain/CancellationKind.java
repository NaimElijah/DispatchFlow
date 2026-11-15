package com.dispatchflow.liveops.domain;

/**
 * What took a stop out of the plan before a late report reached it: its order cancelled or
 * rescheduled, or its plan cancelled. A rescheduled order is treated as a cancelled one (BR-88,
 * BR-90, BR-130, ADR-107).
 */
public enum CancellationKind {
    /** The order was cancelled (FR-207). */
    ORDER_CANCELLED,
    /** The order moved to another date (FR-315). */
    ORDER_RESCHEDULED,
    /** The published plan was cancelled (FR-336). */
    PLAN_CANCELLED
}
