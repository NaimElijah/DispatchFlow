package com.dispatchflow.fleet.domain;

/**
 * Whether a vehicle or a driver may be rostered or registered, as the use case read it before the
 * call (DL §4.2). For a vehicle it is the fleet register's answer; for a driver, Access's answer
 * that the account is active and holds the Driver role, together with the driver record's own
 * state.
 */
public enum Standing {
    /** An active vehicle, or an active driver whose account is active and holds the Driver role. */
    ACTIVE,
    /** Anything else: retired, deactivated, never registered, or without the Driver role. */
    NOT_ACTIVE
}
