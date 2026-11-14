package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A projected arrival rounded to the nearest quarter hour, half up (FR-351, ADR-108): the arrival
 * an "out for delivery" or "time changed" message gives, as the middle of a window 30 minutes
 * either side of it.
 *
 * <p>Israel's offsets from UTC are whole hours, so a quarter hour of Israel time is a quarter hour
 * of UTC, and rounding the instant rounds the wall-clock time, on clock-change days too.
 *
 * @param at the rounded arrival, on a quarter hour
 */
public record QuarterHour(Instant at) {

    /** A quarter hour, in seconds. */
    private static final long QUARTER = Duration.ofMinutes(15).toSeconds();

    /** How far a window reaches either side of its quarter hour (FR-351). */
    private static final Duration HALF_WINDOW = Duration.ofMinutes(30);

    /**
     * Checks the time is on a quarter hour.
     *
     * @throws StoredStateInvalid when it is not, which only a damaged stored message gives
     */
    public QuarterHour {
        Objects.requireNonNull(at, "at");
        if (at.getNano() != 0 || Math.floorMod(at.getEpochSecond(), QUARTER) != 0) {
            throw new StoredStateInvalid(QuarterHour.class, "an arrival sent is on a quarter hour");
        }
    }

    /**
     * Rounds a projected arrival to the nearest quarter hour, half up: 10:07:29 gives 10:00, and
     * 10:07:30 gives 10:15.
     *
     * @param arrival the projected arrival
     * @return the quarter hour
     */
    public static QuarterHour nearest(ProjectedArrival arrival) {
        long seconds = arrival.at().getEpochSecond();
        long below = seconds - Math.floorMod(seconds, QUARTER);
        boolean halfOrMore = 2 * (seconds - below) >= QUARTER;
        return new QuarterHour(Instant.ofEpochSecond(halfOrMore ? below + QUARTER : below));
    }

    /**
     * The window's start, 30 minutes before, in Israel time (FR-351).
     *
     * @return the wall-clock time
     */
    public LocalTime windowStart() {
        return at.minus(HALF_WINDOW).atZone(IsraelDateTime.ZONE).toLocalTime();
    }

    /**
     * The window's end, 30 minutes after, in Israel time (FR-351).
     *
     * @return the wall-clock time
     */
    public LocalTime windowEnd() {
        return at.plus(HALF_WINDOW).atZone(IsraelDateTime.ZONE).toLocalTime();
    }

    /**
     * How far a projection is from this arrival, either way (FR-353).
     *
     * @param arrival the projection
     * @return the distance, never negative
     */
    public Duration distanceTo(ProjectedArrival arrival) {
        return Duration.between(at, arrival.at()).abs();
    }
}
