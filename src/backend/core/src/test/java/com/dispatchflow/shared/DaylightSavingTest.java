package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Israel's 2027 clock changes (BR-31, BR-111, ADR-088). The dates are checked against the JDK's
 * own tzdata first, so a tzdata change fails here, not in a planning test.
 */
class DaylightSavingTest {

    private static final ZoneRules ISRAEL = IsraelDateTime.ZONE.getRules();
    private static final DeliveryDate SPRING_FORWARD = DeliveryDate.of(LocalDate.of(2027, 3, 26));
    private static final DeliveryDate FALL_BACK = DeliveryDate.of(LocalDate.of(2027, 10, 31));
    private static final long HOUR = 3_600;

    @Test
    void GivenTheJdkTzdata_When2027IsRead_ThenTheClocksChangeOn26MarchAnd31October() {
        ZoneOffsetTransition spring = ISRAEL.nextTransition(Instant.parse("2027-01-01T00:00:00Z"));
        ZoneOffsetTransition autumn = ISRAEL.nextTransition(spring.getInstant());

        assertThat(spring.isGap()).isTrue();
        assertThat(spring.getDateTimeBefore()).isEqualTo(LocalDateTime.of(2027, 3, 26, 2, 0));
        assertThat(spring.getDateTimeAfter()).isEqualTo(LocalDateTime.of(2027, 3, 26, 3, 0));
        assertThat(autumn.isOverlap()).isTrue();
        assertThat(autumn.getDateTimeBefore()).isEqualTo(LocalDateTime.of(2027, 10, 31, 2, 0));
        assertThat(autumn.getDateTimeAfter()).isEqualTo(LocalDateTime.of(2027, 10, 31, 1, 0));
    }

    @Test
    void GivenTheSpringForwardDay_WhenItsLengthIsRead_ThenItIs23Hours() {
        assertThat(SPRING_FORWARD.lengthInSeconds()).isEqualTo(82_800);
    }

    @Test
    void GivenTheFallBackDay_WhenItsLengthIsRead_ThenItIs25Hours() {
        assertThat(FALL_BACK.lengthInSeconds()).isEqualTo(90_000);
    }

    @Test
    void GivenAnOrdinaryDay_WhenItsLengthIsRead_ThenItIs24Hours() {
        assertThat(DeliveryDate.of(LocalDate.of(2027, 6, 15)).lengthInSeconds()).isEqualTo(86_400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"02:00", "02:30", "02:59:59"})
    void GivenATimeInTheSkippedHour_WhenAnIsraelTimeIsBuilt_ThenNonexistentLocalTime(String time) {
        LocalDateTime skipped = SPRING_FORWARD.value().atTime(LocalTime.parse(time));

        assertRefused(() -> IsraelDateTime.of(skipped), new NonexistentLocalTime(skipped));
    }

    @ParameterizedTest
    @ValueSource(strings = {"01:59:59", "03:00"})
    void GivenATimeBesideTheSkippedHour_WhenAnIsraelTimeIsBuilt_ThenItExists(String time) {
        LocalDateTime beside = SPRING_FORWARD.value().atTime(LocalTime.parse(time));

        assertThat(IsraelDateTime.of(beside).localDateTime()).isEqualTo(beside);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01:00", "01:30", "01:59:59"})
    void GivenATimeInTheRepeatedHour_WhenConvertedToAnInstant_ThenItIsTheFirstOccurrence(String time) {
        LocalDateTime repeated = FALL_BACK.value().atTime(LocalTime.parse(time));

        Instant instant = IsraelDateTime.of(repeated).toInstant();

        assertThat(instant).isEqualTo(repeated.toInstant(ZoneOffset.ofHours(3)));
    }

    @Test
    void GivenTheSecondOccurrenceOfTheRepeatedHour_WhenReadInIsrael_ThenItReadsBackAsTheFirst() {
        Instant secondOccurrence = Instant.parse("2027-10-30T23:30:00Z");

        IsraelDateTime local = IsraelDateTime.at(secondOccurrence);

        assertThat(local.localDateTime()).isEqualTo(LocalDateTime.of(2027, 10, 31, 1, 30));
        assertThat(local.toInstant()).isEqualTo(Instant.parse("2027-10-30T22:30:00Z"));
    }

    @Test
    void GivenNineOClockOnEachChangeDay_WhenCountedFromMidnight_ThenItIs8And10HoursOfElapsedTime() {
        assertThat(DaySeconds.at(IsraelDateTime.of(SPRING_FORWARD, LocalTime.of(9, 0)))
                        .seconds())
                .isEqualTo(8 * HOUR);
        assertThat(DaySeconds.at(IsraelDateTime.of(FALL_BACK, LocalTime.of(9, 0)))
                        .seconds())
                .isEqualTo(10 * HOUR);
    }

    @Test
    void GivenTheRepeatedHour_WhenCountedFromMidnight_ThenTheFirstOccurrenceCounts() {
        assertThat(DaySeconds.at(IsraelDateTime.of(FALL_BACK, LocalTime.of(1, 30)))
                        .seconds())
                .isEqualTo(HOUR + HOUR / 2);
        assertThat(DaySeconds.at(IsraelDateTime.of(FALL_BACK, LocalTime.of(2, 0)))
                        .seconds())
                .isEqualTo(3 * HOUR);
    }

    @Test
    void GivenTheLastSecondOfEachChangeDay_WhenBuilt_ThenItIsWithinTheDay() {
        assertThat(DaySeconds.of(SPRING_FORWARD, 82_799).toInstant()).isEqualTo(Instant.parse("2027-03-26T20:59:59Z"));
        assertThat(DaySeconds.of(FALL_BACK, 89_999).toInstant()).isEqualTo(Instant.parse("2027-10-31T21:59:59Z"));
    }
}
