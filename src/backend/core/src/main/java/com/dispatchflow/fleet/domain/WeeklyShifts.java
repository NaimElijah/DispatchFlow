package com.dispatchflow.fleet.domain;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A driver's weekly shift template (FR-79, ADR-103): for each weekday, at most one shift's hours.
 * It holds no vehicle: the driver's default vehicle fills that (FR-06). The days are kept in
 * {@link DayOfWeek} order, Monday first, so a snapshot or a screen lists them the same way every
 * time.
 *
 * @param days the shift hours of each weekday that has a shift
 */
public record WeeklyShifts(Map<DayOfWeek, ShiftHours> days) {

    /** Copies the days into weekday order, and makes them unmodifiable. */
    public WeeklyShifts {
        // Built from an empty EnumMap, because EnumMap's copy constructor refuses an empty plain map.
        EnumMap<DayOfWeek, ShiftHours> ordered = new EnumMap<>(DayOfWeek.class);
        Objects.requireNonNull(days, "days")
                .forEach((day, hours) ->
                        ordered.put(Objects.requireNonNull(day, "day"), Objects.requireNonNull(hours, "hours")));
        days = Collections.unmodifiableMap(ordered);
    }

    /**
     * A template with no shift on any day: the driver's default shift applies instead (FR-189).
     *
     * @return the empty template
     */
    public static WeeklyShifts none() {
        return new WeeklyShifts(Map.of());
    }

    /**
     * Creates a template.
     *
     * @param days the shift hours of each weekday that has a shift; days left out have none
     * @return the template
     */
    public static WeeklyShifts of(Map<DayOfWeek, ShiftHours> days) {
        return new WeeklyShifts(days);
    }

    /**
     * Whether no weekday has a shift.
     *
     * @return {@code true} for the empty template
     */
    public boolean isEmpty() {
        return days.isEmpty();
    }

    /**
     * The shift hours of a weekday.
     *
     * @param day the weekday
     * @return its hours, or empty when the template gives that day no shift
     */
    public Optional<ShiftHours> shiftOn(DayOfWeek day) {
        return Optional.ofNullable(days.get(day));
    }
}
