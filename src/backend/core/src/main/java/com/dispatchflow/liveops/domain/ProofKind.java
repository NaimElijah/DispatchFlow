package com.dispatchflow.liveops.domain;

/** The kinds of proof of delivery (FR-30, FR-367). */
public enum ProofKind {
    /** The recipient signed. */
    SIGNATURE,
    /** The driver took a photo, the only proof for a stop left at the door (FR-368). */
    PHOTO
}
