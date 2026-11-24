package com.dispatchflow.fleet.application.api;

/**
 * Whether a driver may be planned now, as Fleet answers it: Access's answer on the account combined
 * with the driver record's own state (ADR-062, ADR-103, DL §4.2). Only an {@link #ACTIVE} driver is
 * planned. When more than one reason holds, the first in this order is given: {@link #ANONYMIZED},
 * {@link #RETIRED}, {@link #NOT_A_DRIVER}.
 *
 * <p>To Be Implemented.
 */
public enum DriverStandingFact {
    /** The account is active and holds the Driver role, and the driver record is active. */
    ACTIVE,
    /**
     * The account is deactivated or doesn't hold the Driver role, or it has no driver record. The
     * record counts again once the account holds the role again (ADR-062).
     */
    NOT_A_DRIVER,
    /** The driver record is retired, so it is not offered for new plans (FR-193). */
    RETIRED,
    /** The driver record is anonymized (BR-98, ADR-103). */
    ANONYMIZED
}
