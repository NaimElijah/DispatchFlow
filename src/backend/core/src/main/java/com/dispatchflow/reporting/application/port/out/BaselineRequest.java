package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.orders.application.api.OrderFact;
import com.dispatchflow.planning.application.api.BaselineInputsFact;
import com.dispatchflow.reporting.domain.WithdrawnFleet;
import com.dispatchflow.shared.PersonalData;
import java.util.List;
import java.util.Objects;

/**
 * What the optimizer's nearest-neighbour baseline is solved from: the same day's orders and fleet
 * as the plan (FR-39, FR-271, FR-342, ADR-052, placement P5). The listener builds it from
 * Planning's {@code PlanFigures.inputsOf}, which names the plan's orders and gives the fleet its
 * daily solve was given, and from Customers &amp; Orders' {@code OrdersForDate}, which gives each
 * of those orders' location, window, demand, service time, priority and door flag.
 *
 * <p>Open for M5: the matrix and the travel values the plan was solved with (ADR-052) are not in
 * it. {@code PlanFigures.inputsOf} doesn't carry them and Reporting can't read Planning's settings,
 * so M5 decides how the baseline gets the plan's travel model. The orders' locations are personal
 * data, so it prints redacted.
 *
 * <p>Built in M5.
 *
 * @param inputs the plan, its date, the orders it covers, and its fleet with each driver's shift
 *     and depots
 * @param orders each order of {@code inputs} still uncancelled, as {@code OrdersForDate} gives it;
 *     an order with no location drops as missing location (BR-14)
 * @param withdrawn the vehicles the solve leaves out and those it gives their whole shift (FR-271);
 *     both empty for a baseline computed at publication
 */
public record BaselineRequest(BaselineInputsFact inputs, List<OrderFact> orders, WithdrawnFleet withdrawn)
        implements PersonalData {

    /** Checks the inputs and the withdrawn fleet are present, and copies the orders. */
    public BaselineRequest {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(withdrawn, "withdrawn");
        orders = List.copyOf(orders);
    }

    /**
     * A form that hides the orders' and the depots' locations.
     *
     * @return {@code BaselineRequest[redacted]}
     */
    @Override
    public String toString() {
        return "BaselineRequest[redacted]";
    }
}
