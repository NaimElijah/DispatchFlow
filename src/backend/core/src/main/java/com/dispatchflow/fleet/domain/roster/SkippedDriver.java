package com.dispatchflow.fleet.domain.roster;

import com.dispatchflow.fleet.domain.FleetError;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DomainError;
import java.util.Objects;

/**
 * A driver whose default a roster fill could not use, and why (ADR-103).
 *
 * @param driver the driver
 * @param reason the named error: {@link FleetError.NotAnActiveDriver}, {@link
 *     FleetError.VehicleNotActive}, {@link FleetError.VehicleAlreadyAssigned}, or the kernel's
 *     {@code NonexistentLocalTime} for a shift in the hour the clock skips (BR-111)
 */
public record SkippedDriver(AccountId driver, DomainError reason) {

    /** Checks both are present. */
    public SkippedDriver {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(reason, "reason");
    }
}
