package com.dispatchflow.reporting.domain.baseline;

import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Optional;

/**
 * What a test sees of a {@link Baseline}, read through its getters: a value that compares by every
 * part. Tests compare a baseline before and after a call, or with the state they expect, through
 * it.
 *
 * @param plan the plan it is the baseline of
 * @param version the version it was stored at, or empty when it was never saved
 * @param result the figures it was last computed from
 * @param cause why it was last computed
 * @param computedAt when it was last computed
 */
public record BaselineDescription(
        PlanId plan, Optional<Version> version, PlannedFigures result, BaselineCause cause, Instant computedAt) {

    /**
     * Describes a baseline.
     *
     * @param baseline the baseline
     * @return what its getters give
     */
    public static BaselineDescription of(Baseline baseline) {
        return new BaselineDescription(
                baseline.plan(), baseline.savedVersion(), baseline.result(), baseline.cause(), baseline.computedAt());
    }
}
