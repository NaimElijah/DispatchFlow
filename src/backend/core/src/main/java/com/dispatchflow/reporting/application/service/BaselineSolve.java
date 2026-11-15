package com.dispatchflow.reporting.application.service;

import com.dispatchflow.reporting.application.port.out.BaselineBudget;
import com.dispatchflow.reporting.application.port.out.BaselineSeed;

/**
 * The seed and the budget every baseline of every plan is solved with, so a recompute replays under
 * the same ones as the publication's baseline, and asking again with the same request gets the same
 * answer (FR-39, NFR-13). {@link ComputeBaseline} and {@link RecomputeBaseline} both build their
 * {@code BaselineRequest} with them.
 */
final class BaselineSolve {

    /** The one seed of every baseline. */
    static final BaselineSeed SEED = new BaselineSeed(0L);

    /** One solution: the nearest-neighbour construction ends with the first one it builds. */
    static final BaselineBudget BUDGET = new BaselineBudget(1);

    private BaselineSolve() {}
}
