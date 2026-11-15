package com.dispatchflow.fleet.domain.roster;

import com.dispatchflow.fleet.domain.FleetError;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * The roster's answer to whether a driver can take a vehicle on its date, as a hand-over needs
 * (FR-302, DL §3.10): {@link DayRoster#checkAssignable} gives it and changes nothing. It stays
 * inside Fleet: Fleet's application layer maps it to the type its {@code api} publishes (DL §2.3,
 * rule 1).
 */
public sealed interface Assignability {

    /**
     * The driver may take the vehicle.
     *
     * @param shift their shift that date, which a hand-over checks the route against (FR-304)
     */
    record Assignable(Shift shift) implements Assignability {

        /** Checks the shift is present. */
        public Assignable {
            Objects.requireNonNull(shift, "shift");
        }
    }

    /**
     * The driver may not take the vehicle.
     *
     * @param reason why: {@code NotAnActiveDriver}, {@code DriverNotRostered}, {@code
     *     DriverMarkedUnavailable} or {@code DriverAlreadyHasVehicle}
     */
    record NotAssignable(FleetError reason) implements Assignability {

        /** Checks the reason is present. */
        public NotAssignable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The driver's shift, or the refusal, for a caller that stops at a refusal.
     *
     * @return the shift of an {@link Assignable} answer
     * @throws RuleViolation with the reason of a {@link NotAssignable} answer
     */
    default Shift orRefuse() {
        return switch (this) {
            case Assignable assignable -> assignable.shift();
            case NotAssignable refused -> throw new RuleViolation(refused.reason());
        };
    }
}
