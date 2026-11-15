package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import java.util.List;
import java.util.Objects;

/**
 * What Reporting needs from Planning to ask the optimizer for the baseline: a nearest-neighbour
 * solve of the same day, on the same orders and fleet (FR-39, FR-271, FR-342, ADR-052, ADR-109,
 * placement P5). It holds only what Reporting can read nowhere else:
 *
 * <ul>
 *   <li>which orders the plan covers, served or dropped; each order's location, window, demand,
 *       service time, priority and door flag come from Customers &amp; Orders' {@code
 *       OrdersForDate}, which Reporting reads itself;
 *   <li>the fleet the plan's daily solve was given, with each vehicle's driver's shift, because
 *       Reporting doesn't depend on Fleet. Reporting's {@code WithdrawnFleet} then leaves out or
 *       gives a whole shift to the withdrawn vehicles (FR-271).
 * </ul>
 *
 * <p>Built in M5.
 *
 * @param plan the plan
 * @param date the plan's delivery date
 * @param orders the orders of the plan's current revision, served or dropped, each once
 * @param vehicles the vehicles the plan's daily solve was given, each once
 * @param depots every depot those vehicles start or end at, each once
 */
public record BaselineInputsFact(
        PlanId plan,
        DeliveryDate date,
        List<OrderId> orders,
        List<BaselineVehicleFact> vehicles,
        List<BaselineDepotFact> depots) {

    /** Checks the plan and the date are present, and copies the lists. */
    public BaselineInputsFact {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        orders = List.copyOf(orders);
        vehicles = List.copyOf(vehicles);
        depots = List.copyOf(depots);
    }
}
