package com.dispatchflow.liveops.domain;

/** The problems a driver can report, as FR-95 names them (ADR-107). */
public enum DriverProblem {
    /** The vehicle broke down. */
    BREAKDOWN,
    /** The driver is running late. */
    RUNNING_LATE,
    /** The vehicle is full. */
    VEHICLE_FULL
}
