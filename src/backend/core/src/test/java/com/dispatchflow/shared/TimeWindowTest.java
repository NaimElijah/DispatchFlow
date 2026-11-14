package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.InvalidTimeWindow;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeWindowTest {

    private static final DeliveryDate DAY = DeliveryDate.of(LocalDate.of(2027, 6, 15));
    private static final DeliveryDate NEXT_DAY = DeliveryDate.of(LocalDate.of(2027, 6, 16));

    @Test
    void GivenAStartBeforeAnEndOnOneDate_WhenBuilt_ThenItLiesOnThatDate() {
        TimeWindow window = TimeWindow.on(DAY, LocalTime.of(9, 0), LocalTime.of(12, 0));

        assertThat(window.date()).isEqualTo(DAY);
        assertThat(window.start()).isEqualTo(IsraelDateTime.of(DAY, LocalTime.of(9, 0)));
        assertThat(window.end()).isEqualTo(IsraelDateTime.of(DAY, LocalTime.of(12, 0)));
    }

    @Test
    void GivenTheWholeDay_WhenBuilt_ThenItIsKept() {
        TimeWindow window = TimeWindow.on(DAY, LocalTime.MIDNIGHT, LocalTime.of(23, 59, 59));

        assertThat(window.date()).isEqualTo(DAY);
    }

    @Test
    void GivenAStartEqualToTheEnd_WhenBuilt_ThenInvalidTimeWindow() {
        IsraelDateTime nine = IsraelDateTime.of(DAY, LocalTime.of(9, 0));

        assertRefused(() -> new TimeWindow(nine, nine), new InvalidTimeWindow(nine, nine));
    }

    @Test
    void GivenAStartAfterTheEnd_WhenBuilt_ThenInvalidTimeWindow() {
        IsraelDateTime twelve = IsraelDateTime.of(DAY, LocalTime.of(12, 0));
        IsraelDateTime nine = IsraelDateTime.of(DAY, LocalTime.of(9, 0));

        assertRefused(() -> new TimeWindow(twelve, nine), new InvalidTimeWindow(twelve, nine));
    }

    @Test
    void GivenAnEndOnTheNextDate_WhenBuilt_ThenInvalidTimeWindow() {
        IsraelDateTime lateEvening = IsraelDateTime.of(DAY, LocalTime.of(22, 0));
        IsraelDateTime nextMidnight = IsraelDateTime.of(NEXT_DAY, LocalTime.MIDNIGHT);

        assertRefused(
                () -> new TimeWindow(lateEvening, nextMidnight), new InvalidTimeWindow(lateEvening, nextMidnight));
    }

    @Test
    void GivenAnEndInTheSkippedHour_WhenBuiltFromWallClockTimes_ThenNonexistentLocalTime() {
        DeliveryDate springForward = DeliveryDate.of(LocalDate.of(2027, 3, 26));

        assertRefused(
                () -> TimeWindow.on(springForward, LocalTime.of(1, 0), LocalTime.of(2, 30)),
                new NonexistentLocalTime(springForward.value().atTime(2, 30)));
    }
}
