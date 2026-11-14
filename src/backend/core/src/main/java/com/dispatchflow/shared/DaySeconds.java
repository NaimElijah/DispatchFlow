package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.OutsideTheDay;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Elapsed seconds since a date's local midnight in Israel, the unit a solve works in (DL §10.1,
 * ADR-088). On a clock-change day 09:00 is 8 or 10 hours after midnight, which is what travel
 * durations need; so this is not a wall-clock offset, and adding it to local midnight as one is an
 * hour wrong on those days.
 *
 * <p>It runs from 0 to the date's length inclusive: the date's length is the end of a solve's
 * planning horizon (ADR-088).
 *
 * @param date the date the seconds count from
 * @param seconds elapsed seconds since that date's local midnight
 */
public record DaySeconds(DeliveryDate date, long seconds) {

    /**
     * Checks the seconds lie within the date.
     *
     * @throws RuleViolation with {@link OutsideTheDay} when {@code seconds} is negative or more
     *     than the date's length
     */
    public DaySeconds {
        Objects.requireNonNull(date, "date");
        if (seconds < 0 || seconds > date.lengthInSeconds()) {
            throw new RuleViolation(new OutsideTheDay(date, seconds));
        }
    }

    /**
     * Creates the value.
     *
     * @param date the date the seconds count from
     * @param seconds elapsed seconds since that date's local midnight
     * @return the value
     * @throws RuleViolation with {@link OutsideTheDay} when {@code seconds} is negative or more
     *     than the date's length
     */
    public static DaySeconds of(DeliveryDate date, long seconds) {
        return new DaySeconds(date, seconds);
    }

    /**
     * The elapsed seconds from a time's own date's midnight to that time, in whole seconds; a
     * fraction of a second is dropped.
     *
     * @param time the Israel time
     * @return the seconds since its date's local midnight
     */
    public static DaySeconds at(IsraelDateTime time) {
        DeliveryDate date = time.date();
        return new DaySeconds(
                date, Duration.between(date.startInstant(), time.toInstant()).toSeconds());
    }

    /**
     * The elapsed seconds from a date's local midnight to an instant. Unlike {@link
     * #at(IsraelDateTime)}, it keeps the second occurrence of a repeated hour apart from the first,
     * so a fact's real time on a fall-back day counts right.
     *
     * <p>A fraction of a second is dropped, so the next midnight plus half a second reads as the
     * date's length, which is still within the day.
     *
     * @param date the date to count from
     * @param instant the instant, on that date
     * @return the whole seconds since the date's local midnight
     * @throws RuleViolation with {@link OutsideTheDay} when the instant is not on the date
     */
    public static DaySeconds at(DeliveryDate date, Instant instant) {
        return new DaySeconds(
                date, Duration.between(date.startInstant(), instant).toSeconds());
    }

    /**
     * The instant these seconds name.
     *
     * @return the date's local midnight plus the elapsed seconds
     */
    public Instant toInstant() {
        return date.startInstant().plusSeconds(seconds);
    }
}
