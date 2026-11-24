package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.baseline.WithdrawnFleet;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * What the optimizer's nearest-neighbour baseline is solved from: the same day's orders and fleet
 * as the plan (FR-39, FR-271, FR-342, ADR-052). The listener builds it from Reporting's own {@link
 * BaselineInputs}, which {@link ReportingViews#baselineInputsOf} reads from the views Planning and
 * Customers &amp; Orders publish (ADR-041, ADR-109), and from {@code Baseline.withdrawnFleetAfter},
 * with the seed and the budget that Reporting's application fixes for every baseline, so a
 * recompute replays under the same ones (NFR-13).
 *
 * <p>Still open until the baseline comparison is built: it has no field yet for the matrix and the
 * travel values the plan was solved with (ADR-052), the door-window rate, the break flag, the fixed
 * vehicle cost or the departure allowance. Reporting can't read Planning's {@code
 * SolveConfiguration} or settings (ADR-120 P5). It will read the scoring values, the door-window
 * rate and the break flag, through a port of its own, bound to the same configuration as Planning's
 * (ADR-121); that port is not declared yet. How the baseline gets the plan's travel model, fixed
 * vehicle cost and departure allowance is decided when the baseline's travel inputs are built. The
 * orders' and the depots' locations are personal data, so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param inputs the plan, its date, its orders, and its fleet with each driver's shift and depots;
 *     an order with no location drops as missing location (BR-14)
 * @param withdrawn the vehicles the solve leaves out and those it gives their whole shift (FR-271);
 *     both empty for a baseline computed at publication
 * @param seed the seed the baseline is solved with (NFR-13)
 * @param budget what ends the baseline's construction (ADR-044)
 */
public record BaselineRequest(BaselineInputs inputs, WithdrawnFleet withdrawn, BaselineSeed seed, BaselineBudget budget)
        implements PersonalData {

    /** Checks every part is present. */
    public BaselineRequest {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(withdrawn, "withdrawn");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(budget, "budget");
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
