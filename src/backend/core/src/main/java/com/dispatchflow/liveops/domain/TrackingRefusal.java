package com.dispatchflow.liveops.domain;

/** Why a route keeps no position from a sender at a time (FR-328, NFR-30). */
public enum TrackingRefusal {
    /** The sender no longer drives the route, after a hand-over (NFR-30). */
    NOT_THE_CURRENT_DRIVER,
    /** The driver was marked unavailable (NFR-30). */
    DRIVER_UNAVAILABLE,
    /** The time is before the run's start, after its finish, or an hour or more past shift end (FR-328). */
    OUTSIDE_WINDOW
}
