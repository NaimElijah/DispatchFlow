package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.TimeBeforeTheDay;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;

/**
 * A time the optimizer reports, as elapsed seconds from a delivery date's local midnight in Israel,
 * the unit a solve works in (DL §10.1, ADR-088). Unlike the kernel's {@code DaySeconds} it has no
 * upper bound: the validator caps no time at the date's end, so a route that breaks a time rule may
 * arrive, serve or return past it, and so may a drop's fact (ADR-112, ADR-113). A time that must lie
 * in the day, such as a window's end or a shift's, stays a {@code DaySeconds}.
 *
 * @param date the date the seconds count from
 * @param seconds elapsed seconds since that date's local midnight, 0 or more
 */
public record SolveTime(DeliveryDate date, long seconds) {

    /**
     * Checks the time.
     *
     * @throws RuleViolation with {@link TimeBeforeTheDay} when {@code seconds} is negative
     */
    public SolveTime {
        Objects.requireNonNull(date, "date");
        if (seconds < 0) {
            throw new RuleViolation(new TimeBeforeTheDay(date, seconds));
        }
    }

    /**
     * Creates the time.
     *
     * @param date the date the seconds count from
     * @param seconds elapsed seconds since that date's local midnight
     * @return the time
     * @throws RuleViolation with {@link TimeBeforeTheDay} when {@code seconds} is negative
     */
    public static SolveTime of(DeliveryDate date, long seconds) {
        return new SolveTime(date, seconds);
    }

    /**
     * Whether the time is after the date's end, which a screen shows as the next day's.
     *
     * @return {@code true} when it is more than the date's length in seconds
     */
    public boolean pastTheDay() {
        return seconds > date.lengthInSeconds();
    }
}
