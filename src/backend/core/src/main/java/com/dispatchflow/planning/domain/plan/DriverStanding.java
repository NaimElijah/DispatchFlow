package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.shared.AccountId;
import java.util.Objects;

/**
 * The driver a route is handed to, as Access and Fleet answered before the hand-over (FR-302, DL
 * §4.4). Whether the hand-over breaks their shift comes from the optimizer's evaluation instead
 * (FR-304, ADR-101).
 *
 * @param driver the driver's account
 * @param activeDriver whether the account is active and holds the Driver role
 * @param available whether the driver is available on the plan's date
 */
public record DriverStanding(AccountId driver, boolean activeDriver, boolean available) {

    /** Checks the driver is present. */
    public DriverStanding {
        Objects.requireNonNull(driver, "driver");
    }
}
