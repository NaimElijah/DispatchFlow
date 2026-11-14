package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import java.util.Objects;

/**
 * When a driver works on one delivery date (DL §17): shift hours placed on that date, so the shift
 * ends after it starts and lies on its date (FR-190). Both ends are Israel times (BR-31): an end in
 * the hour the clock skips is refused, and an end in the hour it repeats reads as its first
 * occurrence (BR-111).
 *
 * @param date the date the shift lies on
 * @param hours its wall-clock hours
 */
public record Shift(DeliveryDate date, ShiftHours hours) {

    /**
     * Checks both ends exist on the date.
     *
     * @throws RuleViolation with {@link NonexistentLocalTime} when the start or the end falls in the
     *     hour the clock skips on that date
     */
    public Shift {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(hours, "hours");
        IsraelDateTime.of(date, hours.start());
        IsraelDateTime.of(date, hours.end());
    }

    /**
     * Places shift hours on a date.
     *
     * @param date the date
     * @param hours the wall-clock hours
     * @return the shift
     * @throws RuleViolation with {@link NonexistentLocalTime} when the start or the end falls in the
     *     hour the clock skips on that date
     */
    public static Shift on(DeliveryDate date, ShiftHours hours) {
        return new Shift(date, hours);
    }

    /**
     * When the shift starts.
     *
     * @return the start, as an Israel time
     */
    public IsraelDateTime start() {
        return IsraelDateTime.of(date, hours.start());
    }

    /**
     * When the shift ends.
     *
     * @return the end, as an Israel time
     */
    public IsraelDateTime end() {
        return IsraelDateTime.of(date, hours.end());
    }

    /**
     * The start in elapsed seconds since the date's midnight, the unit a solve works in (DL §10.1).
     * On a clock-change day it differs from the wall-clock time by an hour.
     *
     * @return the start's seconds within the day
     */
    public DaySeconds startInDay() {
        return DaySeconds.at(start());
    }

    /**
     * The end in elapsed seconds since the date's midnight (DL §10.1).
     *
     * @return the end's seconds within the day
     */
    public DaySeconds endInDay() {
        return DaySeconds.at(end());
    }
}
