package com.dispatchflow.fleet.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.fleet.domain.FleetError.InvalidDepotHours;
import com.dispatchflow.fleet.domain.FleetError.InvalidShift;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * Shifts, their hours and depot hours (FR-190, FR-129, BR-31, BR-111, ADR-103), on an ordinary day
 * and on Israel's two clock-change days of 2027.
 */
class ShiftTest {

    private static final DeliveryDate ORDINARY_DAY = DeliveryDate.of(LocalDate.of(2027, 6, 15));
    private static final DeliveryDate SPRING_FORWARD = DeliveryDate.of(LocalDate.of(2027, 3, 26));
    private static final DeliveryDate FALL_BACK = DeliveryDate.of(LocalDate.of(2027, 10, 31));
    private static final long HOUR = 3_600;

    @Test
    void GivenAStartBeforeAnEnd_WhenShiftHoursAreBuilt_ThenTheyAreKept() {
        ShiftHours hours = ShiftHours.of(LocalTime.of(8, 0), LocalTime.of(16, 0));

        assertThat(hours.start()).isEqualTo(LocalTime.of(8, 0));
        assertThat(hours.end()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void GivenAnEndEqualToTheStart_WhenShiftHoursAreBuilt_ThenInvalidShift() {
        assertRefused(
                () -> ShiftHours.of(LocalTime.of(8, 0), LocalTime.of(8, 0)),
                new InvalidShift(LocalTime.of(8, 0), LocalTime.of(8, 0)));
    }

    @Test
    void GivenAnEndBeforeTheStart_WhenShiftHoursAreBuilt_ThenInvalidShift() {
        assertRefused(
                () -> ShiftHours.of(LocalTime.of(16, 0), LocalTime.of(8, 0)),
                new InvalidShift(LocalTime.of(16, 0), LocalTime.of(8, 0)));
    }

    @Test
    void GivenShiftHoursOnAnOrdinaryDay_WhenPlacedOnTheDate_ThenTheShiftLiesOnThatDate() {
        Shift shift = Shift.on(ORDINARY_DAY, ShiftHours.of(LocalTime.of(8, 0), LocalTime.of(16, 0)));

        assertThat(shift.date()).isEqualTo(ORDINARY_DAY);
        assertThat(shift.start()).isEqualTo(IsraelDateTime.of(ORDINARY_DAY, LocalTime.of(8, 0)));
        assertThat(shift.end()).isEqualTo(IsraelDateTime.of(ORDINARY_DAY, LocalTime.of(16, 0)));
        assertThat(shift.startInDay().seconds()).isEqualTo(8 * HOUR);
        assertThat(shift.endInDay().seconds()).isEqualTo(16 * HOUR);
    }

    @Test
    void GivenAShiftStartingInTheSkippedHour_WhenPlacedOn26March2027_ThenNonexistentLocalTime() {
        ShiftHours hours = ShiftHours.of(LocalTime.of(2, 30), LocalTime.of(10, 0));

        assertRefused(
                () -> Shift.on(SPRING_FORWARD, hours),
                new NonexistentLocalTime(SPRING_FORWARD.value().atTime(2, 30)));
    }

    @Test
    void GivenAShiftEndingInTheSkippedHour_WhenPlacedOn26March2027_ThenNonexistentLocalTime() {
        ShiftHours hours = ShiftHours.of(LocalTime.of(0, 30), LocalTime.of(2, 0));

        assertRefused(
                () -> Shift.on(SPRING_FORWARD, hours),
                new NonexistentLocalTime(SPRING_FORWARD.value().atTime(2, 0)));
    }

    @Test
    void GivenAShiftAcrossTheSkippedHour_WhenPlacedOn26March2027_ThenItLastsAnHourLessThanItsWallClockHours() {
        Shift shift = Shift.on(SPRING_FORWARD, ShiftHours.of(LocalTime.of(1, 30), LocalTime.of(3, 30)));

        assertThat(shift.startInDay().seconds()).isEqualTo(HOUR + HOUR / 2);
        assertThat(shift.endInDay().seconds()).isEqualTo(2 * HOUR + HOUR / 2);
    }

    @Test
    void GivenAShiftStartingInTheRepeatedHour_WhenPlacedOn31October2027_ThenItStartsAtTheFirstOccurrence() {
        Shift shift = Shift.on(FALL_BACK, ShiftHours.of(LocalTime.of(1, 30), LocalTime.of(9, 0)));

        assertThat(shift.start().toInstant()).isEqualTo(Instant.parse("2027-10-30T22:30:00Z"));
        assertThat(shift.startInDay().seconds()).isEqualTo(HOUR + HOUR / 2);
        assertThat(shift.endInDay().seconds()).isEqualTo(10 * HOUR);
    }

    @Test
    void GivenAShiftWithinTheRepeatedHour_WhenPlacedOn31October2027_ThenItIsValidAndLastsItsWallClockMinutes() {
        Shift shift = Shift.on(FALL_BACK, ShiftHours.of(LocalTime.of(1, 0), LocalTime.of(1, 45)));

        assertThat(shift.endInDay().seconds() - shift.startInDay().seconds()).isEqualTo(45 * 60);
    }

    @Test
    void GivenTheSameShiftHours_WhenPlacedOnEachDay_ThenOnlyTheSpringForwardDayRefusesThem() {
        ShiftHours hours = ShiftHours.of(LocalTime.of(2, 15), LocalTime.of(11, 0));

        assertThat(Shift.on(ORDINARY_DAY, hours).date()).isEqualTo(ORDINARY_DAY);
        assertThat(Shift.on(FALL_BACK, hours).date()).isEqualTo(FALL_BACK);
        assertRefused(
                () -> Shift.on(SPRING_FORWARD, hours),
                new NonexistentLocalTime(SPRING_FORWARD.value().atTime(2, 15)));
    }

    @Test
    void GivenAnOpeningBeforeAClosing_WhenDepotHoursAreBuilt_ThenTheyAreKept() {
        DepotHours hours = DepotHours.of(LocalTime.of(6, 0), LocalTime.of(20, 0));

        assertThat(hours.opens()).isEqualTo(LocalTime.of(6, 0));
        assertThat(hours.closes()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void GivenAClosingNotAfterTheOpening_WhenDepotHoursAreBuilt_ThenInvalidDepotHours() {
        assertRefused(
                () -> DepotHours.of(LocalTime.of(20, 0), LocalTime.of(6, 0)),
                new InvalidDepotHours(LocalTime.of(20, 0), LocalTime.of(6, 0)));
        assertRefused(
                () -> DepotHours.of(LocalTime.of(6, 0), LocalTime.of(6, 0)),
                new InvalidDepotHours(LocalTime.of(6, 0), LocalTime.of(6, 0)));
    }
}
