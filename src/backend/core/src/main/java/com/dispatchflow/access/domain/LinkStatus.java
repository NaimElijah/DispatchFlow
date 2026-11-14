package com.dispatchflow.access.domain;

/**
 * Where a credential link stands (DL §4.1, §9.7). An open link past its expiry is expired, which is
 * read from its expiry time, not stored.
 */
public enum LinkStatus {
    /** Sent and not yet used or voided. */
    OPEN,
    /** Used once; it works no more (NFR-211). */
    SPENT,
    /** A newer link of its kind, or a change of email or password, voided it (NFR-224). */
    VOIDED
}
