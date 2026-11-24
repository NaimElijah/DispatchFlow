package com.dispatchflow.planning.application.port.out.settings;

import com.dispatchflow.planning.domain.plan.SolverName;
import java.util.List;
import java.util.Objects;

/**
 * The solvers a dispatcher may choose for a solve or a re-optimization, and the one each kind of solve
 * runs when none is chosen, which the planning settings hold (FR-20, FR-224, ADR-004). Outside the
 * optimizer a solver is known only by its name.
 *
 * <p>{@code PlanningSettings} has no default solver of each kind yet: a domain gap that the solver
 * choice closes.
 *
 * <p>To Be Implemented.
 *
 * @param solvers the names the optimizer offers, in its order
 * @param dailyDefault the solver a daily solve without one runs, from the planning settings
 * @param reoptimizationDefault the solver a re-optimization without one runs, from the planning
 *     settings
 */
public record SolversView(List<SolverName> solvers, SolverName dailyDefault, SolverName reoptimizationDefault) {

    /** Checks both defaults are present, and copies the names. */
    public SolversView {
        solvers = List.copyOf(solvers);
        Objects.requireNonNull(dailyDefault, "dailyDefault");
        Objects.requireNonNull(reoptimizationDefault, "reoptimizationDefault");
    }
}
