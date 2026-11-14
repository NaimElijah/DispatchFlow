package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.InvalidTimeWindow;
import java.time.LocalTime;
import java.util.Objects;

/**
 * When something may happen on one date: a start strictly before an end, both on the same date in
 * Israel (BR-35). Whether the window lies on its order's date is the order's rule, which compares
 * {@link #date()}. A window that crosses midnight would need FR-114, which is not built.
 *
 * @param start the first moment of the window
 * @param end the moment the window closes
 */
public record TimeWindow(IsraelDateTime start, IsraelDateTime end) {

    /**
     * Checks the window.
     *
     * @throws RuleViolation with {@link InvalidTimeWindow} when the start is not before the end, or
     *     the two lie on different dates
     */
    public TimeWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end) || !start.date().equals(end.date())) {
            throw new RuleViolation(new InvalidTimeWindow(start, end));
        }
    }

    /**
     * Creates a window from two wall-clock times on one date.
     *
     * @param date the date
     * @param start the opening time
     * @param end the closing time
     * @return the window
     * @throws RuleViolation with {@link SharedKernelError.NonexistentLocalTime} when either time is
     *     in the skipped hour, or with {@link InvalidTimeWindow} when the start is not before the end
     */
    public static TimeWindow on(DeliveryDate date, LocalTime start, LocalTime end) {
        return new TimeWindow(IsraelDateTime.of(date, start), IsraelDateTime.of(date, end));
    }

    /**
     * The date the window lies on.
     *
     * @return the date of both ends
     */
    public DeliveryDate date() {
        return start.date();
    }
}
