package com.dispatchflow.access.application.api;

/**
 * A role the acting user holds, as other modules see it (DL §17, FR-02, FR-293). Access's domain
 * keeps its own copy, {@code Role}.
 *
 * <p>Built in M0.
 */
public enum ActorRole {
    /** Manages accounts and the company's settings. */
    ADMIN,
    /** Plans and runs the day. */
    DISPATCHER,
    /** Drives routes and works their own route only (BR-02). */
    DRIVER
}
