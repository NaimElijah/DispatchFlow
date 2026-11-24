package com.dispatchflow.fleet.application.api;

import com.dispatchflow.shared.TimeWindow;
import java.util.Objects;

/**
 * The roster's answer to whether a driver can take a vehicle on a date, which Planning asks before
 * it hands a route over (FR-302, DL §4.2). Fleet builds it from {@code DayRoster.checkAssignable},
 * given the driver's standing, and changes nothing (DL §2.3 rule 1).
 *
 * <p>To Be Implemented.
 */
public sealed interface AssignabilityFact {

    /**
     * Why the roster refuses a driver, mirroring {@code DayRoster.checkAssignable}'s refusals.
     *
     * <p>To Be Implemented.
     */
    enum Reason {
        /** The driver's standing is not active: the account or the driver record (ADR-062). */
        NOT_AN_ACTIVE_DRIVER,
        /** The driver has no shift on the date's roster. */
        DRIVER_NOT_ROSTERED,
        /** The driver is marked unavailable that date (FR-300). */
        DRIVER_MARKED_UNAVAILABLE,
        /** The driver drives another vehicle that date (FR-54, FR-190). */
        DRIVER_ALREADY_HAS_VEHICLE
    }

    /**
     * The driver may take the vehicle.
     *
     * <p>To Be Implemented.
     *
     * @param shift their shift on the date, as Israel times on that date, which the hand-over's
     *     evaluation judges the route against (FR-304, ADR-112)
     */
    record Assignable(TimeWindow shift) implements AssignabilityFact {

        /** Checks the shift is present. */
        public Assignable {
            Objects.requireNonNull(shift, "shift");
        }
    }

    /**
     * The driver may not take the vehicle.
     *
     * <p>To Be Implemented.
     *
     * @param reason why
     */
    record NotAssignable(Reason reason) implements AssignabilityFact {

        /** Checks the reason is present. */
        public NotAssignable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
