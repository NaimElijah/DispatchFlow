package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.plan.SolverName;
import java.util.List;
import java.util.Objects;

/**
 * The solvers a dispatcher may choose for a solve or a re-optimization, and the one used when none is
 * chosen (FR-20, FR-224, placement P3). Outside the optimizer a solver is known only by its name.
 *
 * <p>Built in M4.
 *
 * @param solvers the names the optimizer offers, in its order
 * @param defaultSolver the solver a request without one runs: {@link SolverName#OR_TOOLS}
 */
public record SolversView(List<SolverName> solvers, SolverName defaultSolver) {

    /** Checks the default is present, and copies the names. */
    public SolversView {
        solvers = List.copyOf(solvers);
        Objects.requireNonNull(defaultSolver, "defaultSolver");
    }
}
