package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.domain.solution.LateStop;
import com.dispatchflow.planning.domain.solution.ObjectiveTerms;
import com.dispatchflow.planning.domain.solution.SolveFailure;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The optimizer's answer to a re-optimization: a validated solution with what the optimizer
 * computed for the preview, or why it failed, which leaves the plan as it was (FR-36, BR-26,
 * ADR-050). The optimizer adapter builds it from the optimizer's answer alone.
 *
 * <p>It is not the {@code ReoptimizationResult}: the worker, {@code RunSolveJob}, builds that from
 * this answer and the facts it gathered, as the result's own contract says. The worker adds the
 * request's fact time (BR-25), each withdrawn vehicle's cause from its {@code VehicleState} in the
 * request (FR-267, FR-271), and the per-driver changes and goods hand-overs it finds by comparing
 * the solution's routes with the request's (FR-36, FR-334).
 *
 * <p>Built in M9.
 */
public sealed interface ReoptimizationAnswer {

    /**
     * The validator accepted the solution under the re-optimization conventions (BR-15, ADR-050).
     * The adapter builds the report's excluded vehicles from the optimizer's {@code
     * Solved.excluded}, as for a daily solve; a vehicle excluded as withdrawn is one the worker
     * lists in the result's {@code withdrawn} (FR-197, FR-267).
     *
     * <p>Built in M9.
     *
     * @param solution the validated routes, drops and excluded vehicles
     * @param objective the shared objective's terms, route stability included (BR-09, BR-115,
     *     FR-103)
     * @param lateStops the stops it keeps past their window end, from the validator's report
     *     (BR-107)
     * @param withoutUsablePosition the vehicles with no usable position, which the optimizer judged
     *     (BR-80, BR-81)
     * @param stats how the search ran (NFR-11, NFR-13)
     */
    record Solved(
            ValidatedSolution solution,
            ObjectiveTerms objective,
            List<LateStop> lateStops,
            List<VehicleId> withoutUsablePosition,
            SolveStats stats)
            implements ReoptimizationAnswer {

        /** Checks every part is present, and copies the lists. */
        public Solved {
            Objects.requireNonNull(solution, "solution");
            Objects.requireNonNull(objective, "objective");
            lateStops = List.copyOf(lateStops);
            withoutUsablePosition = List.copyOf(withoutUsablePosition);
            Objects.requireNonNull(stats, "stats");
        }
    }

    /**
     * The re-optimization failed (BR-26, BR-84).
     *
     * <p>The optimizer's own {@code Failed} outcome, its solver failed or found no solution within
     * its budget, comes with a closed kind, {@code SOLVER_FAILED} or {@code
     * NO_SOLUTION_WITHIN_BUDGET}, which the adapter maps into a {@code SolveFailureReason}. No
     * reason names it yet: M0 adds one, a domain change and an FR-65 edit, which is the owner's
     * decision. Until then the adapter must not map it to {@code VALIDATOR_REJECTED} with no
     * violations.
     *
     * <p><b>Domain gap.</b> {@code SolveJob.fail} keeps no stats, which NFR-11 records with every
     * solve: M0 adds them beside the failure, and {@code RunSolveJob} passes them on.
     *
     * <p>Built in M9.
     *
     * @param failure why
     * @param stats how the search ran, up to its failure, whenever the optimizer answered: a
     *     validator rejection or its own failure; empty when it was unreachable or overran, since
     *     no answer came (NFR-11)
     */
    record Failed(SolveFailure failure, Optional<SolveStats> stats) implements ReoptimizationAnswer {

        /** Checks both parts are present, the stats possibly empty. */
        public Failed {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(stats, "stats");
        }
    }
}
