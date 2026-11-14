package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.VehicleId;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The values Reporting's tests share. The day is Sunday 13 June 2027, when Israel is at UTC+3, so
 * the 10:00 to 12:00 window is 07:00Z to 09:00Z. Every instant is written as a literal, so no
 * expected value comes from the code under test.
 */
final class ReportingFixtures {

    static final DeliveryDate DAY = DeliveryDate.of(LocalDate.of(2027, 6, 13));
    static final TimeWindow WINDOW = TimeWindow.on(DAY, LocalTime.of(10, 0), LocalTime.of(12, 0));
    static final Instant WINDOW_START = Instant.parse("2027-06-13T07:00:00Z");
    static final Instant WINDOW_END = Instant.parse("2027-06-13T09:00:00Z");

    static final PlanId PLAN = PlanId.of(UUID.fromString("0190a5e2-0000-7000-a000-000000000001"));
    static final OrderId ORDER_A = order(1);
    static final OrderId ORDER_B = order(2);
    static final OrderId ORDER_C = order(3);
    static final OrderId ORDER_D = order(4);
    static final StopId STOP_A = stop(1);
    static final StopId STOP_B = stop(2);
    static final StopId STOP_C = stop(3);
    static final StopId STOP_D = stop(4);
    static final VehicleId VEHICLE_1 = vehicle(1);
    static final VehicleId VEHICLE_2 = vehicle(2);

    /** The plan: A at 11:00, B a second after its window, C dropped; one of three on time. */
    static final PlannedFigures PLAN_FIGURES = new PlannedFigures(
            Distance.of(84_250),
            DriverHours.of(Duration.ofMinutes(390)),
            PlannedOrders.of(List.of(
                    served(ORDER_A, 39_600),
                    served(ORDER_B, 43_201),
                    new PlannedOrder(ORDER_C, WINDOW, new PlannedService.Dropped()))));

    /** The baseline: A at 10:30, B at 11:59:59, C at 12:00; three of three on time. */
    static final PlannedFigures BASELINE_FIGURES = new PlannedFigures(
            Distance.of(97_600),
            DriverHours.of(Duration.ofMinutes(435)),
            PlannedOrders.of(List.of(served(ORDER_A, 37_800), served(ORDER_B, 43_199), served(ORDER_C, 43_200))));

    /** A recomputed baseline after a withdrawal: A and B on time, C dropped. */
    static final PlannedFigures RECOMPUTED_FIGURES = new PlannedFigures(
            Distance.of(91_000),
            DriverHours.of(Duration.ofMinutes(400)),
            PlannedOrders.of(List.of(
                    served(ORDER_A, 36_000),
                    served(ORDER_B, 40_000),
                    new PlannedOrder(ORDER_C, WINDOW, new PlannedService.Dropped()))));

    /** The plan after a revision added D: A and D on time, B late, C dropped; two of four on time. */
    static final PlannedFigures PLAN_WITH_ADDED_ORDER = new PlannedFigures(
            Distance.of(88_000),
            DriverHours.of(Duration.ofMinutes(400)),
            PlannedOrders.of(List.of(
                    served(ORDER_A, 39_600),
                    served(ORDER_B, 43_201),
                    new PlannedOrder(ORDER_C, WINDOW, new PlannedService.Dropped()),
                    served(ORDER_D, 40_000))));

    /** The baseline solved again on the same four orders: all four on time. */
    static final PlannedFigures BASELINE_WITH_ADDED_ORDER = new PlannedFigures(
            Distance.of(99_500),
            DriverHours.of(Duration.ofMinutes(450)),
            PlannedOrders.of(List.of(
                    served(ORDER_A, 37_800),
                    served(ORDER_B, 43_199),
                    served(ORDER_C, 43_200),
                    served(ORDER_D, 41_000))));

    private ReportingFixtures() {}

    static PlannedOrder served(OrderId order, long startSeconds) {
        return new PlannedOrder(order, WINDOW, new PlannedService.Served(DaySeconds.of(DAY, startSeconds)));
    }

    static StopResult deliveredAt(StopId stop, OrderId order, Instant arrivedAt) {
        return new StopResult(
                stop,
                order,
                WINDOW,
                false,
                Optional.of(arrivedAt),
                new StopOutcome.Delivered(arrivedAt.plusSeconds(120)));
    }

    static StopResult doorDeliveredAt(StopId stop, OrderId order, Instant arrivedAt) {
        return new StopResult(
                stop,
                order,
                WINDOW,
                true,
                Optional.of(arrivedAt),
                new StopOutcome.Delivered(arrivedAt.plusSeconds(120)));
    }

    static StopResult failed(StopId stop, OrderId order) {
        return new StopResult(stop, order, WINDOW, false, Optional.of(WINDOW_START), new StopOutcome.Failed());
    }

    static StopResult notReported(StopId stop, OrderId order) {
        return new StopResult(stop, order, WINDOW, false, Optional.empty(), new StopOutcome.NotReported());
    }

    private static OrderId order(int n) {
        return OrderId.of(UUID.fromString("0190a5e2-0000-7000-b000-00000000000" + n));
    }

    private static StopId stop(int n) {
        return StopId.of(UUID.fromString("0190a5e2-0000-7000-c000-00000000000" + n));
    }

    private static VehicleId vehicle(int n) {
        return VehicleId.of(UUID.fromString("0190a5e2-0000-7000-d000-00000000000" + n));
    }
}
