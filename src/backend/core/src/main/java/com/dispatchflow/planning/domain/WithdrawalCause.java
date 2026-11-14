package com.dispatchflow.planning.domain;

/** Why a re-optimization withdrew a vehicle mid-route, which the baseline follows (FR-267, FR-271). */
public enum WithdrawalCause {
    /** The vehicle was marked unavailable (FR-07). */
    VEHICLE_UNAVAILABLE,
    /** The vehicle was left without a driver (FR-82, FR-194). */
    NO_DRIVER,
    /** The driver's shift ran out. */
    SHIFT_RAN_OUT
}
