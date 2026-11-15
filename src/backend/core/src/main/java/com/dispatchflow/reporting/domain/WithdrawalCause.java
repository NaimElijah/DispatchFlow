package com.dispatchflow.reporting.domain;

/**
 * Why a revision withdrew a vehicle mid-route, as the baseline reads it (FR-267, FR-271). Reporting
 * owns this rule (ADR-063), so it keeps its own cause, which the listener on {@code PlanRevised}
 * maps one to one from the event's.
 */
public enum WithdrawalCause {
    /** The vehicle was marked unavailable (FR-07): the baseline leaves it out (FR-271). */
    VEHICLE_UNAVAILABLE,
    /** The vehicle was left without a driver (FR-82, FR-194): the baseline leaves it out (FR-271). */
    NO_DRIVER,
    /** The driver's shift ran out: the baseline gives the vehicle its whole shift (FR-271). */
    SHIFT_RAN_OUT
}
