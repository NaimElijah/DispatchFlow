package com.dispatchflow.liveops.domain.delivery;

/** Customers &amp; Orders' answer on whether an order may be left at the door (FR-364, FR-366). */
public enum LeaveAtDoor {
    /** The recipient allows it to be left at the door. */
    ALLOWED,
    /** It is handed over only. */
    NOT_ALLOWED
}
