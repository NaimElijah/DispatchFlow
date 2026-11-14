package com.dispatchflow.fleet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ADR-103 point 4: one optional shift per weekday, and no vehicle. */
class WeeklyShiftsTest {

    private static final ShiftHours MORNING = ShiftHours.of(LocalTime.of(7, 0), LocalTime.of(15, 0));
    private static final ShiftHours LATE = ShiftHours.of(LocalTime.of(11, 0), LocalTime.of(19, 0));

    @Test
    void GivenNoDays_WhenBuilt_ThenItIsEmptyAndHasNoShiftOnAnyDay() {
        WeeklyShifts none = WeeklyShifts.none();

        assertThat(none.isEmpty()).isTrue();
        for (DayOfWeek day : DayOfWeek.values()) {
            assertThat(none.shiftOn(day)).isEmpty();
        }
    }

    @Test
    void GivenSomeDays_WhenRead_ThenEachSetDayHasItsShiftAndTheOthersHaveNone() {
        WeeklyShifts week = WeeklyShifts.of(Map.of(DayOfWeek.SUNDAY, MORNING, DayOfWeek.WEDNESDAY, LATE));

        assertThat(week.isEmpty()).isFalse();
        assertThat(week.shiftOn(DayOfWeek.SUNDAY)).contains(MORNING);
        assertThat(week.shiftOn(DayOfWeek.WEDNESDAY)).contains(LATE);
        assertThat(week.shiftOn(DayOfWeek.MONDAY)).isEmpty();
    }

    @Test
    void GivenDaysInAnyOrder_WhenListed_ThenTheyFollowTheWeekFromMonday() {
        Map<DayOfWeek, ShiftHours> unordered = new LinkedHashMap<>();
        unordered.put(DayOfWeek.FRIDAY, LATE);
        unordered.put(DayOfWeek.SUNDAY, MORNING);
        unordered.put(DayOfWeek.MONDAY, MORNING);

        assertThat(WeeklyShifts.of(unordered).days().keySet())
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void GivenTheSameDaysBuiltTwice_WhenCompared_ThenTheyAreEqual() {
        assertThat(WeeklyShifts.of(Map.of(DayOfWeek.SUNDAY, MORNING)))
                .isEqualTo(WeeklyShifts.of(new LinkedHashMap<>(Map.of(DayOfWeek.SUNDAY, MORNING))));
    }

    @Test
    void GivenTheMapItWasBuiltFrom_WhenThatMapChanges_ThenTheWeekDoesNot() {
        Map<DayOfWeek, ShiftHours> source = new LinkedHashMap<>(Map.of(DayOfWeek.SUNDAY, MORNING));
        WeeklyShifts week = WeeklyShifts.of(source);

        source.put(DayOfWeek.MONDAY, LATE);

        assertThat(week.shiftOn(DayOfWeek.MONDAY)).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> week.days().put(DayOfWeek.TUESDAY, LATE));
    }
}
