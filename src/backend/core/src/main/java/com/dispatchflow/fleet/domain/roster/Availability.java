package com.dispatchflow.fleet.domain.roster;

/** Whether a rostered driver works on the date as planned (FR-82, FR-300). */
public enum Availability {
    /** The driver works their shift. */
    AVAILABLE,
    /**
     * A dispatcher marked the driver unavailable, so a re-optimization redistributes their stops
     * (FR-82) and they count as no driver when the plan is published (FR-194).
     */
    UNAVAILABLE
}
