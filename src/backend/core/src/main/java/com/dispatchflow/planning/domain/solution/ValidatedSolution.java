package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.planning.domain.PlanningError.SolutionRejected;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.shared.RuleViolation;
import java.util.List;

/**
 * A solution the validator accepted, the only thing that makes a solve job succeed (BR-15, DL §13).
 * Its one factory takes a {@link ValidationReport}, which only the optimizer adapter builds, so
 * holding one proves the validator judged it.
 */
public final class ValidatedSolution {

    private final List<SolvedRoute> routes;
    private final List<DroppedOrder> dropped;
    private final List<ExcludedVehicle> excluded;

    private ValidatedSolution(List<SolvedRoute> routes, List<DroppedOrder> dropped, List<ExcludedVehicle> excluded) {
        this.routes = routes;
        this.dropped = dropped;
        this.excluded = excluded;
    }

    /**
     * Takes the solution of an accepted report.
     *
     * @param report the validator's report
     * @return the validated solution
     * @throws RuleViolation with {@link SolutionRejected} when the report has violations (BR-57)
     */
    public static ValidatedSolution of(ValidationReport report) {
        if (!report.accepted()) {
            throw new RuleViolation(new SolutionRejected(report.violations().size()));
        }
        return new ValidatedSolution(report.routes(), report.dropped(), report.excluded());
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

    /**
     * The vehicles the solve left out (FR-197).
     *
     * @return an unmodifiable list
     */
    public List<ExcludedVehicle> excluded() {
        return excluded;
    }
}
