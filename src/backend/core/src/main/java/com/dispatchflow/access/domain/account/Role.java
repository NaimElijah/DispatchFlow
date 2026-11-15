package com.dispatchflow.access.domain.account;

/** A role an account holds; one account may hold several (DL §17, FR-02, FR-293). */
public enum Role {
    /** Manages accounts and the company's settings. */
    ADMIN,
    /** Plans and runs the day. */
    DISPATCHER,
    /** Drives routes and works their own route only (BR-02). */
    DRIVER
}
