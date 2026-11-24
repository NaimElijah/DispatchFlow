package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;

/**
 * The pre-flight check before a solve: the known causes of dropped orders or a failed solve (FR-141,
 * FR-230). It doesn't stop the solve, and a clean check doesn't promise that no order drops
 * (FR-231). It is read from the date's orders and roster now, not from the plan.
 *
 * <p>To Be Implemented.
 *
 * @param plan the plan about to be solved
 * @param date its delivery date
 * @param withoutLocation the orders whose customer has no coordinates (BR-14)
 * @param vehiclesWithoutDriver the vehicles that may drive and no rostered driver drives (FR-54)
 * @param driversWithoutVehicle the rostered, available drivers with no vehicle that date (FR-54)
 * @param largerThanEveryVehicle the orders heavier or larger than every vehicle that may drive
 * @param windowOutsideEveryShift the orders whose window lies outside every rostered shift
 */
public record PreflightView(
        PlanId plan,
        DeliveryDate date,
        List<OrderId> withoutLocation,
        List<VehicleId> vehiclesWithoutDriver,
        List<AccountId> driversWithoutVehicle,
        List<OrderId> largerThanEveryVehicle,
        List<OrderId> windowOutsideEveryShift) {

    /** Checks the plan and the date are present, and copies the lists. */
    public PreflightView {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        withoutLocation = List.copyOf(withoutLocation);
        vehiclesWithoutDriver = List.copyOf(vehiclesWithoutDriver);
        driversWithoutVehicle = List.copyOf(driversWithoutVehicle);
        largerThanEveryVehicle = List.copyOf(largerThanEveryVehicle);
        windowOutsideEveryShift = List.copyOf(windowOutsideEveryShift);
    }
}
