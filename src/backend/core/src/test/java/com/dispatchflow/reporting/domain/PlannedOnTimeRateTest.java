package com.dispatchflow.reporting.domain;

import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_A;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_B;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_C;
import static com.dispatchflow.reporting.domain.ReportingFixtures.PLAN_FIGURES;
import static com.dispatchflow.reporting.domain.ReportingFixtures.WINDOW;
import static com.dispatchflow.reporting.domain.ReportingFixtures.served;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.reporting.domain.ReportingError.InvalidOnTimeCounts;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.TimeWindow;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The planned on-time rate (FR-266): orders served inside their windows over every order of the day,
 * a dropped order counting as not on time, a leave-at-door order judged on its preferred window
 * (FR-370), and cancelled orders left out (BR-30).
 */
class PlannedOnTimeRateTest {

    private static PlannedOnTimeRate rate(List<PlannedOrder> orders) {
        return PlannedOnTimeRate.of(PlannedOrders.of(orders), CancelledOrders.none());
    }

    @Test
    void GivenNoOrders_WhenTheRateIsComputed_ThenItIsUnavailable() {
        assertThat(rate(List.of())).isEqualTo(new PlannedOnTimeRate.Unavailable());
    }

    // --- the window is 10:00 (36,000 s) to 12:00 (43,200 s) on a 24-hour day. The same rule holds
    // for a hard window and a leave-at-door one: a hard window's start is never planned early,
    // because the vehicle waits (BR-03), and a door stop's is when it arrives (BR-122, FR-370).

    static Stream<Arguments> plannedStarts() {
        return Stream.of(
                Arguments.of("a second before the window opens", 35_999L, 0),
                Arguments.of("exactly as the window opens", 36_000L, 1),
                Arguments.of("a second before the window ends", 43_199L, 1),
                Arguments.of("exactly at the window's end", 43_200L, 1),
                Arguments.of("a second after the window ends", 43_201L, 0));
    }

    @ParameterizedTest(name = "planned to start {0}")
    @MethodSource("plannedStarts")
    void GivenAServedOrder_WhenTheRateIsComputed_ThenItIsOnTimeOnlyWhenItStartsInsideItsWindow(
            String when, long startSeconds, int onTime) {
        assertThat(rate(List.of(served(ORDER_A, startSeconds)))).isEqualTo(new PlannedOnTimeRate.Measured(onTime, 1));
    }

    @Test
    void GivenADroppedOrder_WhenTheRateIsComputed_ThenItCountsAsNotOnTime() {
        assertThat(rate(List.of(
                        served(ORDER_A, 39_600), new PlannedOrder(ORDER_B, WINDOW, new PlannedService.Dropped()))))
                .isEqualTo(new PlannedOnTimeRate.Measured(1, 2));
    }

    @Test
    void GivenThePlansFigures_WhenTheirRateIsAsked_ThenItIsOneOfThree() {
        assertThat(PLAN_FIGURES.plannedOnTime(CancelledOrders.none())).isEqualTo(new PlannedOnTimeRate.Measured(1, 3));
    }

    @Test
    void GivenACancelledOrder_WhenTheRateIsComputed_ThenItIsLeftOut() {
        assertThat(PLAN_FIGURES.plannedOnTime(CancelledOrders.of(List.of(ORDER_C))))
                .isEqualTo(new PlannedOnTimeRate.Measured(1, 2));
    }

    @Test
    void GivenEveryOrderCancelled_WhenTheRateIsComputed_ThenItIsUnavailable() {
        assertThat(PLAN_FIGURES.plannedOnTime(CancelledOrders.of(List.of(ORDER_A, ORDER_B, ORDER_C))))
                .isEqualTo(new PlannedOnTimeRate.Unavailable());
    }

    @Test
    void GivenAWindowEndingInTheRepeatedHour_WhenServiceIsPlannedInItsSecondOccurrence_ThenItIsLate() {
        DeliveryDate fallBack = DeliveryDate.of(LocalDate.of(2027, 10, 31));
        TimeWindow window = TimeWindow.on(fallBack, LocalTime.of(0, 30), LocalTime.of(1, 30));
        // 01:30 first occurs 5,400 s after midnight; 01:15 the second time round is 8,100 s after it.
        PlannedOrder atFirstOccurrence =
                new PlannedOrder(ORDER_A, window, new PlannedService.Served(DaySeconds.of(fallBack, 5_400)));
        PlannedOrder inSecondOccurrence =
                new PlannedOrder(ORDER_B, window, new PlannedService.Served(DaySeconds.of(fallBack, 8_100)));

        assertThat(rate(List.of(atFirstOccurrence, inSecondOccurrence)))
                .isEqualTo(new PlannedOnTimeRate.Measured(1, 2));
    }

    @Test
    void GivenTheSpringForwardDay_WhenServiceIsPlannedAt7200Seconds_ThenItIsOnTimeInA0300To0400Window() {
        DeliveryDate springForward = DeliveryDate.of(LocalDate.of(2027, 3, 26));
        TimeWindow window = TimeWindow.on(springForward, LocalTime.of(3, 0), LocalTime.of(4, 0));
        // The clock skips 02:00 to 02:59, so two elapsed hours after midnight read 03:00.
        PlannedOrder order =
                new PlannedOrder(ORDER_A, window, new PlannedService.Served(DaySeconds.of(springForward, 7_200)));

        assertThat(rate(List.of(order))).isEqualTo(new PlannedOnTimeRate.Measured(1, 1));
    }

    static Stream<Arguments> invalidCounts() {
        return Stream.of(
                Arguments.of(0, 0),
                Arguments.of(0, -1),
                Arguments.of(-1, 1),
                Arguments.of(2, 1),
                Arguments.of(201, 200));
    }

    @ParameterizedTest(name = "{0} of {1}")
    @MethodSource("invalidCounts")
    void GivenCountsThatAreNotAShare_WhenAMeasuredPlannedRateIsMade_ThenInvalidOnTimeCounts(int onTime, int orders) {
        assertRefused(() -> new PlannedOnTimeRate.Measured(onTime, orders), new InvalidOnTimeCounts(onTime, orders));
    }

    @Test
    void GivenTheEdgesOfAShare_WhenAMeasuredPlannedRateIsMade_ThenItKeepsBothCounts() {
        assertThat(new PlannedOnTimeRate.Measured(0, 1).onTime()).isZero();
        assertThat(new PlannedOnTimeRate.Measured(200, 200).orders()).isEqualTo(200);
    }
}
