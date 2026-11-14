package com.dispatchflow.reporting.domain;

import com.dispatchflow.reporting.domain.ReportingError.InvalidOnTimeCounts;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.RuleViolation;
import java.time.Instant;
import java.util.Objects;

/**
 * The planned on-time percentage the baseline comparison uses (FR-39, FR-266): orders a plan serves
 * inside their windows, over every order of the day, so a dropped order counts as not on time. It
 * is an exact share with both counts kept, and only a screen rounds it (ADR-109). With no order to
 * count it is {@link Unavailable}.
 *
 * <p>An order is on time when its planned service starts inside its window, both ends included.
 * The rule is the same for a hard window, whose service waits for the window to open (BR-03), and
 * for a leave-at-door order, whose service starts on arrival (FR-370, BR-122). An order cancelled
 * now is left out of both counts (BR-30).
 */
public sealed interface PlannedOnTimeRate {

    /**
     * A share of the day's orders.
     *
     * @param onTime the orders served inside their windows
     * @param orders every order counted, 1 or more
     */
    record Measured(int onTime, int orders) implements PlannedOnTimeRate {

        /**
         * Checks the counts are a share.
         *
         * @throws RuleViolation with {@link InvalidOnTimeCounts} when {@code orders} is below 1, or
         *     {@code onTime} is negative or above it
         */
        public Measured {
            OnTimeCounts.requireShare(onTime, orders);
        }
    }

    /** The day has no order to count. */
    record Unavailable() implements PlannedOnTimeRate {}

    /**
     * Computes the rate.
     *
     * @param orders every order of the day, each once
     * @param cancelled the orders cancelled now
     * @return the share of uncancelled orders served inside their windows, or {@link Unavailable}
     *     when there is none
     */
    static PlannedOnTimeRate of(PlannedOrders orders, CancelledOrders cancelled) {
        Objects.requireNonNull(orders, "orders");
        Objects.requireNonNull(cancelled, "cancelled");
        int counted = 0;
        int onTime = 0;
        for (PlannedOrder order : orders.orders()) {
            if (cancelled.contains(order.order())) {
                continue;
            }
            counted++;
            if (order.service() instanceof PlannedService.Served(DaySeconds start) && insideWindow(order, start)) {
                onTime++;
            }
        }
        return counted == 0 ? new Unavailable() : new Measured(onTime, counted);
    }

    private static boolean insideWindow(PlannedOrder order, DaySeconds start) {
        Instant startsAt = start.toInstant();
        return !startsAt.isBefore(order.window().start().toInstant())
                && !startsAt.isAfter(order.window().end().toInstant());
    }
}
