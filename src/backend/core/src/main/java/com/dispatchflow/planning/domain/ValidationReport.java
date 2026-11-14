package com.dispatchflow.planning.domain;

import java.util.List;

/**
 * The validator's verdict on a solution, with its routes, times and drops (BR-15, BR-57). It
 * mirrors the Python {@code feasibility.ValidationReport}: every violation, never only the first.
 *
 * <p>Only the optimizer adapter builds one, from the optimizer's answer (DL §13): its constructor is
 * private, and an ArchUnit rule allows no other caller of {@link #of}. It is never stored. {@link
 * ValidatedSolution#of} turns an accepted one into the solution a {@link SolveJob} keeps, or a
 * {@link ReoptimizationProposal} holds. {@link Plan} takes routes only from those two aggregates,
 * which only a succeeded job or persistence builds, so no code path hands it an unvalidated
 * solution (BR-15).
 */
public final class ValidationReport {

    private final List<Violation> violations;
    private final List<SolvedRoute> routes;
    private final List<DroppedOrder> dropped;

    private ValidationReport(List<Violation> violations, List<SolvedRoute> routes, List<DroppedOrder> dropped) {
        this.violations = List.copyOf(violations);
        this.routes = List.copyOf(routes);
        this.dropped = List.copyOf(dropped);
    }

    /**
     * Builds the report; only the optimizer adapter may call it (DL §13).
     *
     * @param violations every violation found, in the order found
     * @param routes the solution's routes, with the validator's times
     * @param dropped the solution's drops, each with its reason (FR-18)
     * @return the report
     */
    public static ValidationReport of(
            List<Violation> violations, List<SolvedRoute> routes, List<DroppedOrder> dropped) {
        return new ValidationReport(violations, routes, dropped);
    }

    /**
     * Whether the solution breaks no rule (BR-15).
     *
     * @return {@code true} when there is no violation
     */
    public boolean accepted() {
        return violations.isEmpty();
    }

    /**
     * Every violation.
     *
     * @return an unmodifiable list, in the order found
     */
    public List<Violation> violations() {
        return violations;
    }

    /**
     * The solution's routes.
     *
     * @return an unmodifiable list
     */
    public List<SolvedRoute> routes() {
        return routes;
    }

    /**
     * The solution's drops.
     *
     * @return an unmodifiable list
     */
    public List<DroppedOrder> dropped() {
        return dropped;
    }
}
