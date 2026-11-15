package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import java.util.Objects;

/**
 * Who sent a report, for which route, and when it happened (BR-44, ADR-087). Only a driver's own
 * report has one, so a record a dispatcher makes on a driver's behalf can't start a route run (DL
 * §6.2, ADR-107).
 *
 * @param driver the reporting driver's account
 * @param route the route the report was sent for
 * @param time when it happened, by the server's clock
 */
public record DriverReport(AccountId driver, RouteId route, CorrectedTime time) {

    /** Checks every part is present. */
    public DriverReport {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(time, "time");
    }
}
