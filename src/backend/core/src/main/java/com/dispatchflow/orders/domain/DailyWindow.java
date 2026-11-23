package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.InvalidDailyWindow;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import com.dispatchflow.shared.TimeWindow;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A customer's default time window, as wall-clock times on no particular date (FR-08, BR-49): a
 * start strictly before an end. An order given no window of its own takes this one on its date.
 *
 * <p>It is not a {@link TimeWindow}, which lies on one date. A new customer starts with the company's
 * default, which Access's company profile holds and the application layer passes in (FR-125,
 * ADR-104).
 *
 * <p>A customer embeds it, stored in two {@code time} columns. Its constructor checks a rule, so the
 * persistence adapter's instantiator rebuilds it, and a stored pair the rule refuses is an incident
 * (ADR-126).
 *
 * @param start the opening time
 * @param end the closing time
 */
@Embeddable
public record DailyWindow(
        // PostgreSQL reserves "end", so the window names its own columns, as TimeWindow's do.
        @Column(name = "starts_at") LocalTime start,
        @Column(name = "ends_at") LocalTime end) {

    /**
     * Checks the window.
     *
     * @throws RuleViolation with {@link InvalidDailyWindow} when the start is not before the end
     */
    public DailyWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new RuleViolation(new InvalidDailyWindow(start, end));
        }
    }

    /**
     * Creates the window.
     *
     * @param start the opening time
     * @param end the closing time
     * @return the window
     * @throws RuleViolation with {@link InvalidDailyWindow} when the start is not before the end
     */
    public static DailyWindow of(LocalTime start, LocalTime end) {
        return new DailyWindow(start, end);
    }

    /**
     * This window on a date, in Israel (BR-31).
     *
     * @param date the order's date
     * @return the window on that date
     * @throws RuleViolation with {@link NonexistentLocalTime} when either end falls in the hour the
     *     clock skips on that date (BR-111)
     */
    public TimeWindow on(DeliveryDate date) {
        return TimeWindow.on(date, start, end);
    }
}
