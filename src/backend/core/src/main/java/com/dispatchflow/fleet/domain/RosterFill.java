package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.AccountId;
import java.util.List;

/**
 * What {@link DayRoster#fill} did (ADR-103): the drivers it gave a value to, and the drivers it
 * skipped with the reason for each, so the dispatcher sees who still needs setting by hand.
 *
 * @param filled the drivers that got a shift or a vehicle, in the order of the defaults
 * @param skipped the drivers whose default could not be used, in the order of the defaults
 */
public record RosterFill(List<AccountId> filled, List<SkippedDriver> skipped) {

    /** Copies the lists. */
    public RosterFill {
        filled = List.copyOf(filled);
        skipped = List.copyOf(skipped);
    }
}
