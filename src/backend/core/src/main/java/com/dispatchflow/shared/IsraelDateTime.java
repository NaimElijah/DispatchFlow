package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A wall-clock time in Israel, the one zone of the whole system (BR-31, ADR-088). It refuses a time
 * in the hour the clock skips, and reads a time in the hour the clock repeats as its first
 * occurrence (BR-111), so every value names exactly one instant.
 *
 * <p>Its order is the order of the instants it names: within the repeated hour every value reads
 * as the first occurrence, so local order and instant order agree.
 *
 * @param localDateTime the wall-clock date and time
 */
public record IsraelDateTime(LocalDateTime localDateTime) implements Comparable<IsraelDateTime> {

    /** {@code Asia/Jerusalem}: every date, window, shift and schedule is in this zone (ADR-088). */
    public static final ZoneId ZONE = ZoneId.of("Asia/Jerusalem");

    /**
     * Checks the time exists in Israel.
     *
     * @throws RuleViolation with {@link NonexistentLocalTime} for a time in the skipped hour
     */
    public IsraelDateTime {
        Objects.requireNonNull(localDateTime, "localDateTime");
        if (ZONE.getRules().getValidOffsets(localDateTime).isEmpty()) {
            throw new RuleViolation(new NonexistentLocalTime(localDateTime));
        }
    }

    /**
     * Creates the value.
     *
     * @param localDateTime the wall-clock date and time
     * @return the value
     * @throws RuleViolation with {@link NonexistentLocalTime} for a time in the skipped hour
     */
    public static IsraelDateTime of(LocalDateTime localDateTime) {
        return new IsraelDateTime(localDateTime);
    }

    /**
     * Creates the value from a date and a time of day.
     *
     * @param date the delivery date
     * @param time the wall-clock time on that date
     * @return the value
     * @throws RuleViolation with {@link NonexistentLocalTime} for a time in the skipped hour
     */
    public static IsraelDateTime of(DeliveryDate date, LocalTime time) {
        return new IsraelDateTime(date.value().atTime(time));
    }

    /**
     * The wall-clock time in Israel at an instant. An instant in the second occurrence of a repeated
     * hour reads as that hour's wall-clock time, and so converts back to its first occurrence
     * (BR-111).
     *
     * @param instant the instant
     * @return its Israel wall-clock time
     */
    public static IsraelDateTime at(Instant instant) {
        return new IsraelDateTime(LocalDateTime.ofInstant(instant, ZONE));
    }

    /**
     * The calendar date of this time.
     *
     * @return the delivery date it falls on
     */
    public DeliveryDate date() {
        return DeliveryDate.of(localDateTime.toLocalDate());
    }

    /**
     * The time of day.
     *
     * @return the wall-clock time
     */
    public LocalTime time() {
        return localDateTime.toLocalTime();
    }

    /**
     * The instant this time names; in a repeated hour, its first occurrence (BR-111).
     *
     * @return the instant
     */
    public Instant toInstant() {
        return localDateTime.atZone(ZONE).toInstant();
    }

    /**
     * Whether this time comes before another.
     *
     * @param other the time to compare with
     * @return {@code true} when this one is earlier
     */
    public boolean isBefore(IsraelDateTime other) {
        return localDateTime.isBefore(other.localDateTime);
    }

    @Override
    public int compareTo(IsraelDateTime other) {
        return localDateTime.compareTo(other.localDateTime);
    }
}
