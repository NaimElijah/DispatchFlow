package com.dispatchflow.shared;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The calendar day a plan, its orders and its roster are for, in Israel (DL §3.3, §17; BR-31). A
 * date is 23, 24 or 25 hours long, because Israel changes its clocks (ADR-088).
 *
 * @param value the calendar day
 */
public record DeliveryDate(LocalDate value) implements Comparable<DeliveryDate> {

    /** Checks the day is present. */
    public DeliveryDate {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Creates the value.
     *
     * @param value the calendar day
     * @return the delivery date
     */
    public static DeliveryDate of(LocalDate value) {
        return new DeliveryDate(value);
    }

    /**
     * Whether this date is before a given day, such as {@code today} in {@code Asia/Jerusalem}.
     *
     * @param day the day to compare with
     * @return {@code true} when this date is strictly earlier
     */
    public boolean isBefore(LocalDate day) {
        return value.isBefore(day);
    }

    /**
     * The first moment of this date in Israel, from which {@link DaySeconds} count.
     *
     * @return the local start of the day
     */
    public IsraelDateTime midnight() {
        return IsraelDateTime.of(value.atStartOfDay(IsraelDateTime.ZONE).toLocalDateTime());
    }

    /**
     * How long this date is in Israel: 82,800, 86,400 or 90,000 seconds. It is also the planning
     * horizon of a solve for this date (ADR-088).
     *
     * @return the elapsed seconds from this date's local midnight to the next one
     */
    public long lengthInSeconds() {
        return Duration.between(
                        startInstant(), DeliveryDate.of(value.plusDays(1)).startInstant())
                .toSeconds();
    }

    Instant startInstant() {
        return value.atStartOfDay(IsraelDateTime.ZONE).toInstant();
    }

    @Override
    public int compareTo(DeliveryDate other) {
        return value.compareTo(other.value);
    }
}
