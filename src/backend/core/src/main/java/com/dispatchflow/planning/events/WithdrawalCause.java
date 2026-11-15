package com.dispatchflow.planning.events;

/**
 * Why a re-optimization withdrew a vehicle mid-route, which the baseline follows (FR-267, FR-271).
 * {@link PlanRevised} carries it, so it lives beside the event that the domain and Reporting share
 * (DL §7.3).
 */
public enum WithdrawalCause {
    /** The vehicle was marked unavailable (FR-07); the baseline leaves it out (FR-271). */
    VEHICLE_UNAVAILABLE,
    /** The vehicle was left without a driver (FR-82, FR-194); the baseline leaves it out (FR-271). */
    NO_DRIVER,
    /** The driver's shift ran out; the baseline gives the vehicle its whole shift (FR-271). */
    SHIFT_RAN_OUT
}
