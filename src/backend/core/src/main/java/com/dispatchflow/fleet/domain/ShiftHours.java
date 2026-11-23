package com.dispatchflow.fleet.domain;

import com.dispatchflow.fleet.domain.FleetError.InvalidShift;
import com.dispatchflow.shared.RuleViolation;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A shift's wall-clock hours in Israel, not yet placed on a date: a driver's default shift (FR-06,
 * FR-189), one day of their weekly shifts (FR-79), or what a dispatcher types for a date (FR-54).
 * {@link com.dispatchflow.fleet.domain.roster.Shift#on} places them on a date, where a clock change
 * can refuse them.
 *
 * <p>A shift that crosses midnight would need FR-114, which is not built, so the end is a time of
 * the same day.
 *
 * <p>An entity embeds it, stored in two {@code time} columns, {@code starts_at} and {@code ends_at}
 * unless the entity overrides them. Its constructor checks a rule, so the persistence adapter's
 * instantiator rebuilds it, and a stored pair the rule refuses is an incident (ADR-126).
 *
 * @param start when the shift starts
 * @param end when it ends
 */
@Embeddable
public record ShiftHours(
        // PostgreSQL reserves "end", so the hours name their own columns, as TimeWindow's do.
        @Column(name = "starts_at") LocalTime start,
        @Column(name = "ends_at") LocalTime end) {

    /**
     * Checks the end is after the start (FR-190).
     *
     * @throws RuleViolation with {@link InvalidShift} when the end is not after the start
     */
    public ShiftHours {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new RuleViolation(new InvalidShift(start, end));
        }
    }

    /**
     * Creates the hours.
     *
     * @param start when the shift starts
     * @param end when it ends
     * @return the hours
     * @throws RuleViolation with {@link InvalidShift} when the end is not after the start
     */
    public static ShiftHours of(LocalTime start, LocalTime end) {
        return new ShiftHours(start, end);
    }
}
