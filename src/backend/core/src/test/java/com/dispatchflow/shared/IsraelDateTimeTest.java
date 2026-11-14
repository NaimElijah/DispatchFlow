package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** The clock-change cases, NonexistentLocalTime among them, are in {@link DaylightSavingTest}. */
class IsraelDateTimeTest {

    private static final DeliveryDate WINTER_DAY = DeliveryDate.of(LocalDate.of(2027, 1, 12));
    private static final DeliveryDate SUMMER_DAY = DeliveryDate.of(LocalDate.of(2027, 7, 12));

    @Test
    void GivenAWinterTime_WhenConvertedToAnInstant_ThenIsraelIsTwoHoursAheadOfUtc() {
        assertThat(IsraelDateTime.of(WINTER_DAY, LocalTime.of(9, 0)).toInstant())
                .isEqualTo(Instant.parse("2027-01-12T07:00:00Z"));
    }

    @Test
    void GivenASummerTime_WhenConvertedToAnInstant_ThenIsraelIsThreeHoursAheadOfUtc() {
        assertThat(IsraelDateTime.of(SUMMER_DAY, LocalTime.of(9, 0)).toInstant())
                .isEqualTo(Instant.parse("2027-07-12T06:00:00Z"));
    }

    @Test
    void GivenAnInstant_WhenReadInIsrael_ThenItIsTheLocalWallClockTime() {
        IsraelDateTime local = IsraelDateTime.at(Instant.parse("2027-07-12T21:30:00Z"));

        assertThat(local.localDateTime()).isEqualTo(LocalDateTime.of(2027, 7, 13, 0, 30));
        assertThat(local.date()).isEqualTo(DeliveryDate.of(LocalDate.of(2027, 7, 13)));
        assertThat(local.time()).isEqualTo(LocalTime.of(0, 30));
    }

    @Test
    void GivenTwoTimes_WhenCompared_ThenTheEarlierComesFirst() {
        IsraelDateTime nine = IsraelDateTime.of(SUMMER_DAY, LocalTime.of(9, 0));
        IsraelDateTime ten = IsraelDateTime.of(SUMMER_DAY, LocalTime.of(10, 0));

        assertThat(nine.isBefore(ten)).isTrue();
        assertThat(ten.isBefore(nine)).isFalse();
        assertThat(nine.isBefore(nine)).isFalse();
        assertThat(nine).isLessThan(ten);
    }
}
