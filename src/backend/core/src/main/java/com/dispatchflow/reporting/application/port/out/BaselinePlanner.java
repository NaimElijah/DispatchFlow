package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.baseline.PlannedFigures;

/**
 * The optimizer's baseline: a nearest-neighbour solve of a plan's day that meets windows, capacity
 * and shifts, drops what doesn't fit, and passes the validator (FR-39, FR-342, BR-15, TS §6.4).
 * Reporting's optimizer adapter implements it over the client generated from {@code
 * optimization-api} v1, as an anti-corruption layer (ADR-130), and translates the answer into a
 * {@link BaselineAnswer}: Reporting's {@link PlannedFigures},
 * or the optimizer's refusal with its kind mapped into a {@link RefusalCause}.
 *
 * <p>A listener calls it before its transaction opens (DL §6.3). An optimizer that can't be
 * reached fails the call with {@link BaselineOptimizerUnavailable}, which fails the listener's
 * delivery, so Spring Modulith resubmits it (ADR-064, DL §12). An answer the adapter doesn't know,
 * such as a failure kind with no {@link RefusalCause}, is treated the same way.
 *
 * <p>To Be Implemented.
 */
public interface BaselinePlanner {

    /**
     * Solves the baseline.
     *
     * <p>To Be Implemented.
     *
     * @param request the day's orders and fleet, the withdrawn vehicles' treatment, and the seed
     *     and budget
     * @return the baseline's distance, driver-hours and each order's planned service or drop, or
     *     the optimizer's refusal when it found no baseline
     * @throws BaselineOptimizerUnavailable when the optimizer can't be reached, doesn't answer in
     *     time, or answers what the adapter doesn't know
     */
    BaselineAnswer planFor(BaselineRequest request);
}
