package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.OutsideTheDay;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DaySecondsTest {

    private static final DeliveryDate ORDINARY = DeliveryDate.of(LocalDate.of(2027, 6, 15));
    private static final DeliveryDate SPRING_FORWARD = DeliveryDate.of(LocalDate.of(2027, 3, 26));
    private static final DeliveryDate FALL_BACK = DeliveryDate.of(LocalDate.of(2027, 10, 31));

    static Stream<DeliveryDate> dates() {
        return Stream.of(ORDINARY, SPRING_FORWARD, FALL_BACK);
    }

    @ParameterizedTest
    @MethodSource("dates")
    void GivenZero_WhenBuilt_ThenItIsTheDatesMidnight(DeliveryDate date) {
        assertThat(DaySeconds.of(date, 0).toInstant()).isEqualTo(date.midnight().toInstant());
    }

    @ParameterizedTest
    @MethodSource("dates")
    void GivenTheLastSecondOfTheDay_WhenBuilt_ThenItIsKept(DeliveryDate date) {
        long lastSecond = date.lengthInSeconds() - 1;

        assertThat(DaySeconds.of(date, lastSecond).seconds()).isEqualTo(lastSecond);
    }

    @ParameterizedTest
    @MethodSource("dates")
    void GivenTheDaysLength_WhenBuilt_ThenItIsTheEndOfThePlanningHorizon(DeliveryDate date) {
        DaySeconds end = DaySeconds.of(date, date.lengthInSeconds());

        assertThat(end.toInstant())
                .isEqualTo(DeliveryDate.of(date.value().plusDays(1)).midnight().toInstant());
    }

    @ParameterizedTest
    @MethodSource("dates")
    void GivenOnePastTheDaysLength_WhenBuilt_ThenOutsideTheDay(DeliveryDate date) {
        long onePast = date.lengthInSeconds() + 1;

        assertRefused(() -> DaySeconds.of(date, onePast), new OutsideTheDay(date, onePast));
    }

    @Test
    void GivenANegativeCount_WhenBuilt_ThenOutsideTheDay() {
        assertRefused(() -> DaySeconds.of(ORDINARY, -1), new OutsideTheDay(ORDINARY, -1));
    }

    @Test
    void GivenAnIsraelTimeWithAFraction_WhenCounted_ThenTheFractionIsDropped() {
        IsraelDateTime time = IsraelDateTime.of(ORDINARY, LocalTime.of(0, 0, 1, 999_000_000));

        assertThat(DaySeconds.at(time)).isEqualTo(DaySeconds.of(ORDINARY, 1));
    }

    @Test
    void GivenAnIsraelTimeOnAnOrdinaryDay_WhenCountedAndConvertedBack_ThenItIsTheSameInstant() {
        IsraelDateTime time = IsraelDateTime.of(ORDINARY, LocalTime.of(14, 25, 10));

        DaySeconds seconds = DaySeconds.at(time);

        assertThat(seconds.seconds()).isEqualTo(14 * 3_600 + 25 * 60 + 10);
        assertThat(seconds.toInstant()).isEqualTo(time.toInstant());
        assertThat(seconds.toInstant()).isEqualTo(Instant.parse("2027-06-15T11:25:10Z"));
    }
}
