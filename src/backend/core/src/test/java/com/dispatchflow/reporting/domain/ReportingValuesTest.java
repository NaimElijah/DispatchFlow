package com.dispatchflow.reporting.domain;

import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_A;
import static com.dispatchflow.reporting.domain.ReportingFixtures.ORDER_B;
import static com.dispatchflow.reporting.domain.ReportingFixtures.UNAVAILABLE_1;
import static com.dispatchflow.reporting.domain.ReportingFixtures.served;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.reporting.domain.ReportingError.NegativeDistance;
import com.dispatchflow.reporting.domain.ReportingError.NegativeDriverHours;
import com.dispatchflow.reporting.domain.ReportingError.OrderCountedTwice;
import com.dispatchflow.reporting.domain.baseline.BaselineCause;
import com.dispatchflow.reporting.domain.baseline.Distance;
import com.dispatchflow.reporting.domain.baseline.DriverHours;
import com.dispatchflow.reporting.domain.baseline.Withdrawal;
import com.dispatchflow.shared.OrderId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reporting's small values: distances, driver-hours, the cancelled orders and a day's orders. */
class ReportingValuesTest {

    @Test
    void GivenZeroMetres_WhenADistanceIsMade_ThenItHoldsThem() {
        assertThat(Distance.of(0).metres()).isZero();
    }

    @Test
    void GivenOneMetreBelowZero_WhenADistanceIsMade_ThenNegativeDistance() {
        assertRefused(() -> Distance.of(-1), new NegativeDistance(-1));
    }

    @Test
    void GivenZeroTime_WhenDriverHoursAreMade_ThenTheyHoldIt() {
        assertThat(DriverHours.of(Duration.ZERO).value()).isEqualTo(Duration.ZERO);
    }

    @Test
    void GivenOneSecondBelowZero_WhenDriverHoursAreMade_ThenNegativeDriverHours() {
        assertRefused(() -> DriverHours.of(Duration.ofSeconds(-1)), new NegativeDriverHours(-1));
    }

    @Test
    void GivenAnOrderListedTwice_WhenTheDaysOrdersAreMade_ThenOrderCountedTwice() {
        assertRefused(
                () -> PlannedOrders.of(
                        List.of(served(ORDER_A, 39_600), served(ORDER_B, 39_600), served(ORDER_A, 40_000))),
                new OrderCountedTwice(ORDER_A));
    }

    @Test
    void GivenEachOrderOnce_WhenTheDaysOrdersAreMade_ThenTheyKeepTheirOrder() {
        PlannedOrders orders = PlannedOrders.of(List.of(served(ORDER_B, 39_600), served(ORDER_A, 40_000)));

        assertThat(orders.orders()).containsExactly(served(ORDER_B, 39_600), served(ORDER_A, 40_000));
    }

    @Test
    void GivenAListThatChangesLater_WhenCancelledOrdersAreMade_ThenTheyKeepTheirCopy() {
        List<OrderId> given = new ArrayList<>(List.of(ORDER_A));
        CancelledOrders cancelled = CancelledOrders.of(given);

        given.add(ORDER_B);

        assertThat(cancelled.contains(ORDER_A)).isTrue();
        assertThat(cancelled.contains(ORDER_B)).isFalse();
        assertThat(CancelledOrders.none().contains(ORDER_A)).isFalse();
    }

    @Test
    void GivenAListThatChangesLater_WhenARecomputedCauseIsMade_ThenItKeepsItsCopy() {
        List<Withdrawal> given = new ArrayList<>(List.of(UNAVAILABLE_1));
        BaselineCause.Recomputed cause = new BaselineCause.Recomputed(given, List.of());

        given.clear();

        assertThat(cause.withdrawn()).containsExactly(UNAVAILABLE_1);
    }
}
