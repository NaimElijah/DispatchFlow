package com.dispatchflow.liveops.domain.delivery;

/** BR-109's fixed list of failure reasons for the first release. */
public enum FailureReasonKind {
    /** Nobody was there. */
    CUSTOMER_ABSENT,
    /** The address was wrong. */
    WRONG_ADDRESS,
    /** The recipient refused the goods. */
    REFUSED,
    /** The driver couldn't get in. */
    NO_ACCESS,
    /** The goods were damaged. */
    DAMAGED,
    /** Another reason, given as text. */
    OTHER
}
