package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.domain.solution.ReoptimizationResult;
import com.dispatchflow.planning.domain.solution.SolveFailure;
import java.util.Objects;

/**
 * The optimizer's answer to a re-optimization: a validated solution with the preview's facts, or why
 * it failed, which leaves the plan as it was (FR-36, BR-26, ADR-050). The optimizer adapter builds
 * it, with the request's fact time (BR-25).
 *
 * <p>Built in M9.
 */
public sealed interface ReoptimizationAnswer {

    /**
     * The validator accepted the solution under the re-optimization conventions (BR-15, ADR-050).
     *
     * <p>Built in M9.
     *
     * @param result the validated solution and the preview's facts
     */
    record Solved(ReoptimizationResult result) implements ReoptimizationAnswer {

        /** Checks the result is present. */
        public Solved {
            Objects.requireNonNull(result, "result");
        }
    }

    /**
     * The re-optimization failed (BR-26, BR-84).
     *
     * <p>Built in M9.
     *
     * @param failure why
     */
    record Failed(SolveFailure failure) implements ReoptimizationAnswer {

        /** Checks the failure is present. */
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
