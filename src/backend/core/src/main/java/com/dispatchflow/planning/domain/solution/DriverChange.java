package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import java.util.List;
import java.util.Objects;

/**
 * What a re-optimization changes for one driver, for the preview (FR-36, FR-249).
 *
 * @param driver the driver
 * @param added the orders that join their route
 * @param removed the orders that leave it
 * @param reordered the orders that stay and change position
 */
public record DriverChange(AccountId driver, List<OrderId> added, List<OrderId> removed, List<OrderId> reordered) {

    /** Checks the driver is present, and copies the lists. */
    public DriverChange {
        Objects.requireNonNull(driver, "driver");
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        reordered = List.copyOf(reordered);
    }
}
