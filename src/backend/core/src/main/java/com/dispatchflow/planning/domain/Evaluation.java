package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The validator's judgement of a plan as a change would leave it, read for one version of the plan
 * (FR-23, FR-233, BR-21, DL §4.4). It carries every violation and the times of every route and stop,
 * or says the optimizer could not be reached (BR-70, ADR-058). {@link Plan} refuses one read for
 * another version, or one whose routes and stops differ from the plan's after the change
 * ({@code StaleEvaluation}, ADR-101).
 *
 * <p>The validator judges a route's shift by its vehicle's rostered driver, and reports BR-20 when
 * the route names anyone else. So an evaluation of a hand-over must be read with the vehicle
 * rostered to the new driver, although the roster follows only after {@code RouteHandedOver}
 * (FR-191, ADR-112).
 *
 * <p>Only the optimizer adapter builds one (DL §13): its constructor is private, and an ArchUnit
 * rule allows no other caller of its factories. It is never stored.
 */
public final class Evaluation {

    private final Version planVersion;
    private final boolean available;
    private final List<Violation> violations;
    private final List<RouteTimes> routes;

    private Evaluation(Version planVersion, boolean available, List<Violation> violations, List<RouteTimes> routes) {
        this.planVersion = Objects.requireNonNull(planVersion, "planVersion");
        this.available = available;
        this.violations = List.copyOf(violations);
        this.routes = List.copyOf(routes);
    }

    /**
     * The validator's judgement; only the optimizer adapter may call it (DL §13).
     *
     * @param planVersion the plan version the change was made from
     * @param violations every violation, in the order found
     * @param routes every route of the plan after the change, with its stops in order
     * @return the evaluation
     */
    public static Evaluation checked(Version planVersion, List<Violation> violations, List<RouteTimes> routes) {
        return new Evaluation(planVersion, true, violations, routes);
    }

    /**
     * The optimizer could not be reached; only the optimizer adapter may call it (BR-70, ADR-058).
     *
     * @param planVersion the plan version the change was made from
     * @return an evaluation that judged nothing
     */
    public static Evaluation unavailable(Version planVersion) {
        return new Evaluation(planVersion, false, List.of(), List.of());
    }

    /**
     * The plan version it was read for.
     *
     * @return the version
     */
    public Version planVersion() {
        return planVersion;
    }

    /**
     * Whether the validator judged the plan.
     *
     * @return {@code false} when the optimizer could not be reached
     */
    public boolean available() {
        return available;
    }

    /**
     * Every violation found.
     *
     * @return an unmodifiable list; empty when unavailable
     */
    public List<Violation> violations() {
        return violations;
    }

    /**
     * The times of every route.
     *
     * @return an unmodifiable list; empty when unavailable
     */
    public List<RouteTimes> routes() {
        return routes;
    }

    /**
     * Whether the route breaks its driver's shift, the break a hand-over needs the dispatcher to
     * confirm (FR-304, BR-06). BR-105's break reserve alone doesn't make a broken shift (ADR-101).
     * A late return that breaks the shift breaks the reserve too, so a confirmed hand-over waives
     * both (ADR-112).
     *
     * @param route the route
     * @return {@code true} when a BR-06 violation concerns it
     */
    public boolean breaksShift(RouteId route) {
        return violations.stream().anyMatch(violation -> violation.breaks(RuleCode.SHIFTS, route));
    }

    /**
     * When the route returns, as judged (FR-394).
     *
     * @param route the route
     * @return its return time, or empty when it judged no such route
     */
    public Optional<DaySeconds> returnOf(RouteId route) {
        return routes.stream()
                .filter(times -> times.route().equals(route))
                .map(times -> times.plan().returnsAt())
                .findFirst();
    }
}
