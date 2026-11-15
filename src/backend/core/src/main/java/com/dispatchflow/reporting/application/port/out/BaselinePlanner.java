package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.PlannedFigures;

/**
 * The optimizer's baseline: a nearest-neighbour solve of a plan's day that meets windows, capacity
 * and shifts, drops what doesn't fit, and passes the validator (FR-39, FR-342, BR-15, TS §6.4).
 * The optimizer's HTTP client implements it, an anti-corruption layer over {@code optimizer-api}
 * v1, and translates the answer into a {@link BaselineAnswer}: Reporting's {@link PlannedFigures},
 * or the optimizer's refusal.
 *
 * <p>A listener calls it before its transaction opens (DL §6.3). An optimizer that can't be
 * reached fails the call with an unchecked exception, which fails the listener's delivery, so
 * Spring Modulith resubmits it (ADR-064, DL §12).
 *
 * <p>Built in M5.
 */
public interface BaselinePlanner {

    /**
     * Solves the baseline.
     *
     * <p>Built in M5.
     *
     * @param request the day's orders and fleet, and the withdrawn vehicles' treatment
     * @return the baseline's distance, driver-hours and each order's planned service or drop, or the
     *     optimizer's refusal when it found no baseline
     */
    BaselineAnswer planFor(BaselineRequest request);
}
