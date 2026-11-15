package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.planning.domain.solution.Evaluation;

/**
 * The optimizer's validator, asked to judge a plan without optimizing it: after an edit (FR-23,
 * FR-233), for a hand-over (FR-304), for the publishing re-check (BR-21) and for the route re-check
 * (FR-395, ADR-058). The optimizer adapter implements it over {@code optimizer-api}'s {@code
 * /v1/evaluate}, and is the only builder of an {@link Evaluation} (DL §13). The use case calls it
 * before its transaction opens (DL §6.3).
 *
 * <p>Built in M4.
 */
public interface FeasibilityChecker {

    /**
     * Judges a layout. It never fails for an unreachable optimizer: it answers an unavailable
     * evaluation, which an edit refuses, publishing refuses, and a hand-over records as <em>not yet
     * checked</em> (BR-70, FR-395, ADR-058, ADR-101).
     *
     * <p>Built in M4.
     *
     * @param request the layout, its plan version and the date's facts
     * @return every violation and the times of every route and stop the validator could time, or an
     *     unavailable evaluation for the request's version
     */
    Evaluation evaluate(EvaluationRequest request);
}
