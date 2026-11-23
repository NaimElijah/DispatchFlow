package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import java.util.Objects;

/**
 * A driver's arrival at the stop (FR-29, BR-87).
 *
 * @param driver the driver
 * @param route the route the report came from
 * @param time when it happened
 */
public record ArrivalRecord(AccountId driver, RouteId route, CorrectedTime time) {

    /** Checks every part is present. */
    public ArrivalRecord {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(time, "time");
    }
}
