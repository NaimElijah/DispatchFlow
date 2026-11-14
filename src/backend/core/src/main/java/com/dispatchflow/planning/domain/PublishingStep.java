package com.dispatchflow.planning.domain;

/** BR-72's four publishing checks, in the order they run; publishing stops at the first that refuses. */
public enum PublishingStep {
    /** Every route's vehicle is available and its driver is the vehicle's, with the Driver role (BR-20, BR-63). */
    STAFFING,
    /** Every uncancelled order of the date is served or dropped, and none changed since the solve (BR-22, BR-71). */
    ORDERS,
    /** No flagged violation is open (BR-17). */
    VIOLATIONS,
    /** The optimizer's re-check of this version finds nothing broken (BR-21, BR-67, BR-70). */
    RECHECK
}
