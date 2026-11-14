package com.dispatchflow.reporting.domain;

import com.dispatchflow.reporting.domain.ReportingError.InvalidOnTimeCounts;
import com.dispatchflow.reporting.domain.ReportingError.StopCountedTwice;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The daily summary's on-time percentage (FR-38, FR-264): delivered stops reached on time, over
 * delivered stops. It is an exact share with both counts kept, and only a screen rounds it
 * (ADR-109). With no delivered stop it is {@link Unavailable}, never 0 (FR-265).
 *
 * <p>A stop is reached at its reported arrival, or at its delivery when no arrival was reported
 * (FR-340), both as corrected for the device's clock (BR-114). A stop with a hard window is on time
 * when it is reached at or before the window's end, since a vehicle that arrives early waits
 * (FR-264, BR-03). A leave-at-door stop is served on arrival, so it is on time only when it is
 * reached inside its window, both ends included (FR-370, BR-122). A stop of a cancelled order is
 * left out, delivered or not (BR-30), and a failed or unreported stop counts in neither part
 * (FR-339). The rate is computed from the effective outcomes each time it is asked, so a correction
 * made later changes it (FR-333).
 */
public sealed interface OnTimeRate {

    /**
     * A share of delivered stops.
     *
     * @param onTime the delivered stops reached on time
     * @param delivered every delivered stop counted, 1 or more
     */
    record Measured(int onTime, int delivered) implements OnTimeRate {

        /**
         * Checks the counts are a share.
         *
         * @throws RuleViolation with {@link InvalidOnTimeCounts} when {@code delivered} is below 1,
         *     or {@code onTime} is negative or above it
         */
        public Measured {
            OnTimeCounts.requireShare(onTime, delivered);
        }
    }

    /** Nothing was delivered, so there is no share to show (FR-265). */
    record Unavailable() implements OnTimeRate {}

    /**
     * Computes the rate from the stops of one plan.
     *
     * @param stops every stop of the plan, each once; a put-back order may have several (FR-258)
     * @param cancelled the orders cancelled now
     * @return the share of delivered stops of uncancelled orders reached on time, or {@link
     *     Unavailable} when there is none
     * @throws RuleViolation with {@link StopCountedTwice} naming the first stop given twice
     */
    static OnTimeRate of(List<StopResult> stops, CancelledOrders cancelled) {
        Objects.requireNonNull(stops, "stops");
        Objects.requireNonNull(cancelled, "cancelled");
        Set<StopId> seen = new HashSet<>();
        int delivered = 0;
        int onTime = 0;
        for (StopResult stop : stops) {
            if (!seen.add(stop.stop())) {
                throw new RuleViolation(new StopCountedTwice(stop.stop()));
            }
            if (stop.outcome() instanceof StopOutcome.Delivered(Instant deliveredAt)
                    && !cancelled.contains(stop.order())) {
                delivered++;
                if (reachedOnTime(stop, stop.arrivedAt().orElse(deliveredAt))) {
                    onTime++;
                }
            }
        }
        return delivered == 0 ? new Unavailable() : new Measured(onTime, delivered);
    }

    private static boolean reachedOnTime(StopResult stop, Instant reachedAt) {
        boolean byTheEnd = !reachedAt.isAfter(stop.window().end().toInstant());
        boolean notBeforeTheStart = !reachedAt.isBefore(stop.window().start().toInstant());
        return stop.leaveAtDoor() ? byTheEnd && notBeforeTheStart : byTheEnd;
    }
}
