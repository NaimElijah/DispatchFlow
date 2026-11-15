package com.dispatchflow.reporting.domain;

import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_A;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_B;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_C;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_D;
import static com.dispatchflow.reporting.domain.ReportingFixtures.STOP_A;
import static com.dispatchflow.reporting.domain.ReportingFixtures.STOP_B;
import static com.dispatchflow.reporting.domain.ReportingFixtures.STOP_C;
import static com.dispatchflow.reporting.domain.ReportingFixtures.STOP_D;
import static com.dispatchflow.reporting.domain.ReportingFixtures.WINDOW;
import static com.dispatchflow.reporting.domain.ReportingFixtures.WINDOW_END;
import static com.dispatchflow.reporting.domain.ReportingFixtures.WINDOW_START;
import static com.dispatchflow.reporting.domain.ReportingFixtures.deliveredAt;
import static com.dispatchflow.reporting.domain.ReportingFixtures.doorDeliveredAt;
import static com.dispatchflow.reporting.domain.ReportingFixtures.failed;
import static com.dispatchflow.reporting.domain.ReportingFixtures.notReported;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.reporting.domain.ReportingError.InvalidOnTimeCounts;
import com.dispatchflow.reporting.domain.ReportingError.StopCountedTwice;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.TimeWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The daily summary's on-time rate (FR-264, FR-265): reached by the window's end, or inside the
 * window for a leave-at-door stop (FR-370); the delivery time standing in for a missing arrival
 * (FR-340); cancelled orders left out (BR-30); and the effective outcome counted (FR-333).
 */
class OnTimeRateTest {

    private static OnTimeRate rate(List<StopResult> stops) {
        return OnTimeRate.of(stops, CancelledOrders.none());
    }

    // --- unavailable (FR-265)

    @Test
    void GivenNoStops_WhenTheRateIsComputed_ThenItIsUnavailable() {
        assertThat(rate(List.of())).isEqualTo(new OnTimeRate.Unavailable());
    }

    @Test
    void GivenOnlyFailedAndUnreportedStops_WhenTheRateIsComputed_ThenItIsUnavailableNotZero() {
        assertThat(rate(List.of(failed(STOP_A, ORDER_A), notReported(STOP_B, ORDER_B))))
                .isEqualTo(new OnTimeRate.Unavailable());
    }

    // --- a stop with a hard window: at or before the window's end (FR-264)

    static Stream<Arguments> hardWindowArrivals() {
        return Stream.of(
                Arguments.of("an hour before the window opens", Instant.parse("2027-06-13T06:00:00Z"), 1),
                Arguments.of("as the window opens", WINDOW_START, 1),
                Arguments.of("a second before the window ends", Instant.parse("2027-06-13T08:59:59Z"), 1),
                Arguments.of("exactly at the window's end", WINDOW_END, 1),
                Arguments.of("a millisecond after the window ends", Instant.parse("2027-06-13T09:00:00.001Z"), 0),
                Arguments.of("a second after the window ends", Instant.parse("2027-06-13T09:00:01Z"), 0));
    }

    @ParameterizedTest(name = "arrived {0}")
    @MethodSource("hardWindowArrivals")
    void GivenADeliveredStopWithAHardWindow_WhenTheRateIsComputed_ThenItIsOnTimeUpToTheWindowsEnd(
            String when, Instant arrivedAt, int onTime) {
        assertThat(rate(List.of(deliveredAt(STOP_A, ORDER_A, arrivedAt))))
                .isEqualTo(new OnTimeRate.Measured(onTime, 1));
    }

    // --- a leave-at-door stop: inside its preferred window (FR-370, BR-119)

    static Stream<Arguments> doorArrivals() {
        return Stream.of(
                Arguments.of("an hour before the window opens", Instant.parse("2027-06-13T06:00:00Z"), 0),
                Arguments.of("a second before the window opens", Instant.parse("2027-06-13T06:59:59Z"), 0),
                Arguments.of("exactly as the window opens", WINDOW_START, 1),
                Arguments.of("inside the window", Instant.parse("2027-06-13T08:00:00Z"), 1),
                Arguments.of("exactly at the window's end", WINDOW_END, 1),
                Arguments.of("a second after the window ends", Instant.parse("2027-06-13T09:00:01Z"), 0));
    }

    @ParameterizedTest(name = "arrived {0}")
    @MethodSource("doorArrivals")
    void GivenADeliveredLeaveAtDoorStop_WhenTheRateIsComputed_ThenItIsOnTimeOnlyInsideItsWindow(
            String when, Instant arrivedAt, int onTime) {
        assertThat(rate(List.of(doorDeliveredAt(STOP_A, ORDER_A, arrivedAt))))
                .isEqualTo(new OnTimeRate.Measured(onTime, 1));
    }

    // --- the delivery time stands in for a missing arrival (FR-340)

    static Stream<Arguments> deliveriesWithNoArrival() {
        return Stream.of(
                Arguments.of("exactly at the window's end", WINDOW_END, 1),
                Arguments.of("a second after the window ends", Instant.parse("2027-06-13T09:00:01Z"), 0));
    }

    @ParameterizedTest(name = "delivered {0}")
    @MethodSource("deliveriesWithNoArrival")
    void GivenADeliveryWithNoArrivalReport_WhenTheRateIsComputed_ThenTheDeliveryTimeCountsAsTheArrival(
            String when, Instant deliveredAt, int onTime) {
        StopResult stop = new StopResult(
                STOP_A, ORDER_A, WINDOW, false, Optional.empty(), new StopOutcome.Delivered(deliveredAt));

        assertThat(rate(List.of(stop))).isEqualTo(new OnTimeRate.Measured(onTime, 1));
    }

    @Test
    void GivenAnArrivalInsideTheWindowAndADeliveryAfterIt_WhenTheRateIsComputed_ThenTheArrivalDecides() {
        StopResult stop = new StopResult(
                STOP_A,
                ORDER_A,
                WINDOW,
                false,
                Optional.of(WINDOW_END),
                new StopOutcome.Delivered(Instant.parse("2027-06-13T09:05:00Z")));

        assertThat(rate(List.of(stop))).isEqualTo(new OnTimeRate.Measured(1, 1));
    }

    // --- what is counted

    @Test
    void GivenAMixedDay_WhenTheRateIsComputed_ThenOnlyDeliveredStopsOfUncancelledOrdersAreCounted() {
        List<StopResult> stops = List.of(
                deliveredAt(STOP_A, ORDER_A, WINDOW_START),
                deliveredAt(STOP_B, ORDER_B, Instant.parse("2027-06-13T09:30:00Z")),
                failed(STOP_C, ORDER_C),
                notReported(STOP_D, ORDER_D));

        assertThat(rate(stops)).isEqualTo(new OnTimeRate.Measured(1, 2));
    }

    @Test
    void GivenADeliveredStopOfACancelledOrder_WhenTheRateIsComputed_ThenItIsLeftOut() {
        List<StopResult> stops = List.of(
                deliveredAt(STOP_A, ORDER_A, WINDOW_START),
                deliveredAt(STOP_B, ORDER_B, Instant.parse("2027-06-13T09:30:00Z")));

        assertThat(OnTimeRate.of(stops, CancelledOrders.of(List.of(ORDER_B)))).isEqualTo(new OnTimeRate.Measured(1, 1));
    }

    @Test
    void GivenEveryDeliveredStopsOrderCancelled_WhenTheRateIsComputed_ThenItIsUnavailable() {
        List<StopResult> stops = List.of(deliveredAt(STOP_A, ORDER_A, WINDOW_START), failed(STOP_B, ORDER_B));

        assertThat(OnTimeRate.of(stops, CancelledOrders.of(List.of(ORDER_A)))).isEqualTo(new OnTimeRate.Unavailable());
    }

    @Test
    void GivenAFailedStopCorrectedToDelivered_WhenTheRateIsComputedAgain_ThenItFollowsTheCorrection() {
        StopResult before = failed(STOP_A, ORDER_A);
        StopResult corrected = new StopResult(
                STOP_A, ORDER_A, WINDOW, false, Optional.of(WINDOW_START), new StopOutcome.Delivered(WINDOW_END));
        StopResult other = deliveredAt(STOP_B, ORDER_B, Instant.parse("2027-06-13T09:30:00Z"));

        assertThat(rate(List.of(before, other))).isEqualTo(new OnTimeRate.Measured(0, 1));
        assertThat(rate(List.of(corrected, other))).isEqualTo(new OnTimeRate.Measured(1, 2));
    }

    @Test
    void GivenTwoStopsOfOneOrderAfterAPutBack_WhenTheRateIsComputed_ThenEachStopCounts() {
        List<StopResult> stops = List.of(failed(STOP_A, ORDER_A), deliveredAt(STOP_B, ORDER_A, WINDOW_END));

        assertThat(rate(stops)).isEqualTo(new OnTimeRate.Measured(1, 1));
    }

    // --- clock changes: the window's end is an instant (BR-111)

    @Test
    void GivenAWindowEndingInTheRepeatedHour_WhenTheArrivalIsInItsSecondOccurrence_ThenItIsLate() {
        DeliveryDate fallBack = DeliveryDate.of(LocalDate.of(2027, 10, 31));
        TimeWindow window = TimeWindow.on(fallBack, LocalTime.of(0, 30), LocalTime.of(1, 30));
        // 01:30 reads as its first occurrence, at UTC+3; 01:15 the second time round is at UTC+2.
        StopResult atFirstOccurrence = new StopResult(
                STOP_A,
                ORDER_A,
                window,
                false,
                Optional.of(Instant.parse("2027-10-30T22:30:00Z")),
                new StopOutcome.Delivered(Instant.parse("2027-10-30T22:32:00Z")));
        StopResult inSecondOccurrence = new StopResult(
                STOP_B,
                ORDER_B,
                window,
                false,
                Optional.of(Instant.parse("2027-10-30T23:15:00Z")),
                new StopOutcome.Delivered(Instant.parse("2027-10-30T23:17:00Z")));

        assertThat(rate(List.of(atFirstOccurrence, inSecondOccurrence))).isEqualTo(new OnTimeRate.Measured(1, 2));
    }

    // --- refusals

    @Test
    void GivenAStopListedTwice_WhenTheRateIsComputed_ThenStopCountedTwice() {
        assertRefused(
                () -> rate(List.of(
                        deliveredAt(STOP_A, ORDER_A, WINDOW_START), failed(STOP_B, ORDER_B), failed(STOP_A, ORDER_A))),
                new StopCountedTwice(STOP_A));
    }

    static Stream<Arguments> invalidCounts() {
        return Stream.of(
                Arguments.of(0, 0), Arguments.of(0, -1), Arguments.of(-1, 1), Arguments.of(2, 1), Arguments.of(4, 3));
    }

    @ParameterizedTest(name = "{0} of {1}")
    @MethodSource("invalidCounts")
    void GivenCountsThatAreNotAShare_WhenAMeasuredRateIsMade_ThenInvalidOnTimeCounts(int onTime, int delivered) {
        assertRefused(() -> new OnTimeRate.Measured(onTime, delivered), new InvalidOnTimeCounts(onTime, delivered));
    }

    @Test
    void GivenTheEdgesOfAShare_WhenAMeasuredRateIsMade_ThenItKeepsBothCounts() {
        assertThat(new OnTimeRate.Measured(0, 1).onTime()).isZero();
        assertThat(new OnTimeRate.Measured(1, 1).delivered()).isEqualTo(1);
    }
}
