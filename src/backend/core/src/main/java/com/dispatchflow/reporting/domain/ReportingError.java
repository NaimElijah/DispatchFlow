package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;

/**
 * Every refusal of the Reporting context (DL §3.8, §4.7). Each guards a value built from another
 * context's figures, so the fields hold IDs and counts, never text or personal data (ADR-109).
 */
public sealed interface ReportingError extends DomainError {

    /**
     * A distance below zero metres, which no route drives.
     *
     * @param metres the distance given
     */
    record NegativeDistance(long metres) implements ReportingError {}

    /**
     * A driving time below zero, which no route takes.
     *
     * @param seconds the time given, in whole seconds
     */
    record NegativeDriverHours(long seconds) implements ReportingError {}

    /**
     * A measured rate whose counts are not a share: the total is below 1, or the on-time count is
     * negative or above the total. A total of 0 is the {@code Unavailable} rate instead (FR-265).
     *
     * @param onTime the on-time count given
     * @param total the total given
     */
    record InvalidOnTimeCounts(int onTime, int total) implements ReportingError {}

    /**
     * A stop given twice to one on-time rate, which would count it twice (FR-264).
     *
     * @param stop the stop
     */
    record StopCountedTwice(StopId stop) implements ReportingError {}

    /**
     * An order given twice to one plan's figures, which would count it twice (FR-266).
     *
     * @param order the order
     */
    record OrderCountedTwice(OrderId order) implements ReportingError {}
}
