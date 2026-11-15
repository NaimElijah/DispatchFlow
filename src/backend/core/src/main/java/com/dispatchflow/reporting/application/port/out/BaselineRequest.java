package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.WithdrawnFleet;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * What the optimizer's nearest-neighbour baseline is solved from: the same day's orders and fleet
 * as the plan (FR-39, FR-271, FR-342, ADR-052). The listener builds it from Reporting's own {@link
 * BaselineInputs}, which {@link ReportingViews#baselineInputsOf} reads from the views Planning and
 * Customers &amp; Orders publish (ADR-041, ADR-109), and from {@code Baseline.withdrawnFleetAfter}.
 *
 * <p>Open for M5: the matrix and the travel values the plan was solved with (ADR-052) are not in
 * it, and it has no field for them. Reporting can't read Planning's settings, so M5 decides how the
 * baseline gets the plan's travel model. The orders' and the depots' locations are personal data,
 * so it prints redacted.
 *
 * <p>Built in M5.
 *
 * @param inputs the plan, its date, its orders, and its fleet with each driver's shift and depots;
 *     an order with no location drops as missing location (BR-14)
 * @param withdrawn the vehicles the solve leaves out and those it gives their whole shift (FR-271);
 *     both empty for a baseline computed at publication
 */
public record BaselineRequest(BaselineInputs inputs, WithdrawnFleet withdrawn) implements PersonalData {

    /** Checks both parts are present. */
    public BaselineRequest {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(withdrawn, "withdrawn");
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
