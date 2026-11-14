package com.dispatchflow.liveops.events;

/** What started a route run (FR-68, FR-327, DL §6.2). */
public enum RunStartCause {
    /** The driver started the route. */
    DRIVER_STARTED,
    /** The driver's first stop report on a route not started (FR-327). */
    FIRST_REPORT,
    /** A dispatcher confirmed a held report that was the route's first (BR-132, DL §6.2). */
    HELD_REPORT_CONFIRMED
}
