package com.dispatchflow.liveops.events;

/** What a stop's outcome is, the first one winning (DL §17, BR-18, BR-87). */
public enum OutcomeKind {
    /** The goods reached the recipient, or were left at the door (FR-29, FR-366). */
    DELIVERED,
    /** The stop could not be served, with a reason (FR-29, FR-242, BR-109). */
    FAILED
}
