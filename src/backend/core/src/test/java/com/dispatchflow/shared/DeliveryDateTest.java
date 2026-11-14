package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.DateOutOfRange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeliveryDateTest {

    private static final LocalDate DAY = LocalDate.of(2027, 6, 15);

    @Test
    void GivenTheDayBefore_WhenAskedIfBefore_ThenItIs() {
        assertThat(DeliveryDate.of(DAY.minusDays(1)).isBefore(DAY)).isTrue();
    }

    @Test
    void GivenTheSameDay_WhenAskedIfBefore_ThenItIsNot() {
        assertThat(DeliveryDate.of(DAY).isBefore(DAY)).isFalse();
    }

    @Test
    void GivenTheDayAfter_WhenAskedIfBefore_ThenItIsNot() {
        assertThat(DeliveryDate.of(DAY.plusDays(1)).isBefore(DAY)).isFalse();
    }

    @Test
    void GivenADate_WhenItsMidnightIsRead_ThenItIsTheStartOfThatDayInIsrael() {
        assertThat(DeliveryDate.of(DAY).midnight().localDateTime()).isEqualTo(LocalDateTime.of(2027, 6, 15, 0, 0));
    }

    @Test
    void GivenTwoDates_WhenCompared_ThenTheEarlierComesFirst() {
        assertThat(DeliveryDate.of(DAY)).isLessThan(DeliveryDate.of(DAY.plusDays(1)));
        assertThat(DeliveryDate.of(DAY)).isEqualTo(DeliveryDate.of(LocalDate.of(2027, 6, 15)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0001-01-01", "9999-12-31"})
    void GivenTheFirstAndLastSupportedDays_WhenBuilt_ThenTheirLengthIsKnown(String day) {
        assertThat(DeliveryDate.of(LocalDate.parse(day)).lengthInSeconds()).isPositive();
    }

    @Test
    void GivenTheLargestLocalDate_WhenBuilt_ThenDateOutOfRange() {
        assertRefused(() -> DeliveryDate.of(LocalDate.MAX), new DateOutOfRange(LocalDate.MAX));
    }

    @Test
    void GivenTheSmallestLocalDate_WhenBuilt_ThenDateOutOfRange() {
        assertRefused(() -> DeliveryDate.of(LocalDate.MIN), new DateOutOfRange(LocalDate.MIN));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10_000})
    void GivenAYearOutsideOneTo9999_WhenBuilt_ThenDateOutOfRange(int year) {
        LocalDate day = LocalDate.of(year, 1, 1);

        assertRefused(() -> DeliveryDate.of(day), new DateOutOfRange(day));
    }
}
