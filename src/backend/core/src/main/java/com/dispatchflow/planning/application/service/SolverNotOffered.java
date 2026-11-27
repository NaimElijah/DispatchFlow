package com.dispatchflow.planning.application.service;

import com.dispatchflow.planning.application.port.out.solvejob.RouteOptimizer;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.shared.application.ApplicationRefusal;
import java.util.Objects;

/**
 * A dispatcher chose a solver that the optimizer doesn't offer, so the solve or the re-optimization
 * is refused before any job is queued (FR-20, FR-224, ADR-004). {@code RequestSolve} and {@code
 * RequestReoptimization} check the name against {@link RouteOptimizer#solvers()}, which lists only
 * FR-20's solvers, so the baseline's is refused too. It is not a domain refusal, because only the
 * optimizer knows its solvers, so it is no {@code DomainError}: the web adapter answers it with 422
 * and {@link #code()}. The message names the solver only.
 *
 * <p>To Be Implemented.
 */
public final class SolverNotOffered extends RuntimeException implements ApplicationRefusal {

    /** The stable code the web adapter answers with, beside the 422 status. */
    public static final String CODE = "SOLVER_NOT_OFFERED";

    private final SolverName solver;

    /**
     * Creates the error.
     *
     * @param solver the solver the dispatcher chose
     */
    public SolverNotOffered(SolverName solver) {
        super("the optimizer offers no solver "
                + Objects.requireNonNull(solver, "solver").value());
        this.solver = solver;
    }

    /**
     * The stable code.
     *
     * @return {@value #CODE}
     */
    @Override
    public String code() {
        return CODE;
    }

    /**
     * The solver the dispatcher chose, for the problem's {@code params}.
     *
     * @return the solver's name
     */
    public SolverName solver() {
        return solver;
    }
}
