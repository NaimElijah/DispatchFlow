package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.domain.solution.ObjectiveTerms;
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
     * The validator accepted the solution (BR-15). The adapter builds the report's excluded vehicles
     * from the optimizer's {@code Solved.excluded}, each exclusion mapped one to one into an {@code
     * ExclusionCause}, so the solution names them (FR-197).
     *
     * <p><b>Domain gap.</b> {@code SolveJob.succeed} and its {@code SolveResult} keep the routes, the
     * drops and the excluded vehicles only, so neither stores the objective nor the stats that NFR-11
     * records with every solve. M0 adds them to {@code SolveResult}, and {@code RunSolveJob} passes
     * them on.
     *
     * <p>Built in M0.
     *
     * @param solution the validated routes, drops and excluded vehicles
     * @param objective the shared objective's terms, as the optimizer scored the solution (BR-09,
     *     NFR-11)
     * @param stats how the search ran (NFR-11, NFR-13)
     */
    record Solved(ValidatedSolution solution, ObjectiveTerms objective, SolveStats stats) implements DailyAnswer {

        /** Checks every part is present. */
        public Solved {
            Objects.requireNonNull(solution, "solution");
            Objects.requireNonNull(objective, "objective");
            Objects.requireNonNull(stats, "stats");
        }
    }

    /**
     * The solve failed: the optimizer was unreachable, overran, or its validator rejected the solution
     * with every violation (FR-65, BR-57).
     *
     * <p>The optimizer's own {@code Failed} outcome, its solver failed or found no solution within
     * its budget, comes with a closed kind, {@code SOLVER_FAILED} or {@code NO_SOLUTION_WITHIN_BUDGET},
     * which the adapter maps into a {@code SolveFailureReason}. No reason names it yet: M0 adds one, a
     * domain change and an FR-65 edit, which is the owner's decision. Until then the adapter must not
     * map it to {@code VALIDATOR_REJECTED} with no violations.
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
