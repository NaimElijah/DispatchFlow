package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.domain.solution.SolveFailure;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import java.util.Objects;

/**
 * The optimizer's answer to a daily solve: a solution its validator accepted, or why the solve failed
 * (BR-15, BR-57, FR-65). The optimizer adapter builds it, and is the only builder of the {@code
 * ValidationReport} a {@link ValidatedSolution} comes from (DL §13).
 *
 * <p>Built in M0.
 */
public sealed interface DailyAnswer {

    /**
     * The validator accepted the solution (BR-15).
     *
     * <p>Built in M0.
     *
     * @param solution the validated routes, drops and excluded vehicles
     */
    record Solved(ValidatedSolution solution) implements DailyAnswer {

        /** Checks the solution is present. */
        public Solved {
            Objects.requireNonNull(solution, "solution");
        }
    }

    /**
     * The solve failed: the optimizer was unreachable, overran, or its validator rejected the solution
     * with every violation (FR-65, BR-57).
     *
     * <p>Built in M0.
     *
     * @param failure why
     */
    record Failed(SolveFailure failure) implements DailyAnswer {

        /** Checks the failure is present. */
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
