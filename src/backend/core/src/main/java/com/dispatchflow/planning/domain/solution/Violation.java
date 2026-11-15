package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * One broken rule the validator reports, with what it concerns and by how much (BR-16, BR-57, DL §17).
 * It mirrors the Python {@code feasibility.Violation}; the adapter turns the optimizer's indices
 * into these IDs. While open it blocks publishing (BR-17).
 *
 * @param rule the rule broken
 * @param route the route it concerns, if one
 * @param order the order it concerns, if one
 * @param vehicle the vehicle it concerns, if one
 * @param driver the driver it concerns, if one
 * @param by by how much a time limit is passed, for a time rule
 * @param kilograms by how many kilograms the weight capacity is passed (BR-05)
 * @param litres by how many litres the volume capacity is passed (BR-05)
 */
public record Violation(
        RuleCode rule,
        Optional<RouteId> route,
        Optional<OrderId> order,
        Optional<VehicleId> vehicle,
        Optional<AccountId> driver,
        Optional<Duration> by,
        Optional<BigDecimal> kilograms,
        Optional<BigDecimal> litres) {

    /** Checks every part is present, possibly empty. */
    public Violation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(kilograms, "kilograms");
        Objects.requireNonNull(litres, "litres");
    }

    /**
     * A violation of a rule that concerns one route.
     *
     * @param rule the rule broken
     * @param route the route
     * @return the violation, concerning nothing else
     */
    public static Violation onRoute(RuleCode rule, RouteId route) {
        return new Violation(
                rule,
                Optional.of(route),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Whether this violation breaks a rule on a route.
     *
     * @param code the rule
     * @param routeId the route
     * @return {@code true} when both match
     */
    public boolean breaks(RuleCode code, RouteId routeId) {
        return rule.equals(code) && route.filter(routeId::equals).isPresent();
    }
}
