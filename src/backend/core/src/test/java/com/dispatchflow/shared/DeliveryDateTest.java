package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

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
}
