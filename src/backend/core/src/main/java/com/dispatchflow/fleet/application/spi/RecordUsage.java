package com.dispatchflow.fleet.application.spi;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.VehicleId;
import java.time.LocalDate;
import java.util.List;

/**
 * Which plans use a depot, a vehicle or a driver, which Fleet asks before it retires one (FR-305,
 * DL §4.2). Planning implements it as its {@code FleetUsageService} (DL §2.1's dashed arrow,
 * placement P9). Only an uncancelled plan for today or a later date blocks a retirement, which
 * Fleet's {@code PlanUses} decides.
 *
 * <p>To Be Implemented.
 */
public interface RecordUsage {

    /**
     * The plans dated today or later whose routes use a vehicle.
     *
     * <p>To Be Implemented.
     *
     * @param vehicle the vehicle
     * @param today today's date in {@code Asia/Jerusalem}; an earlier plan never blocks
     * @return the plans, cancelled ones included, in any order
     */
    List<PlanUseFact> usesOfVehicle(VehicleId vehicle, LocalDate today);

    /**
     * The plans dated today or later whose routes start or end at a depot.
     *
     * <p>To Be Implemented.
     *
     * @param depot the depot
     * @param today today's date in {@code Asia/Jerusalem}; an earlier plan never blocks
     * @return the plans, cancelled ones included, in any order
     */
    List<PlanUseFact> usesOfDepot(DepotId depot, LocalDate today);

    /**
     * The plans dated today or later whose routes a driver drives.
     *
     * <p>To Be Implemented.
     *
     * @param driver the driver's account
     * @param today today's date in {@code Asia/Jerusalem}; an earlier plan never blocks
     * @return the plans, cancelled ones included, in any order
     */
    List<PlanUseFact> usesOfDriver(AccountId driver, LocalDate today);
}
