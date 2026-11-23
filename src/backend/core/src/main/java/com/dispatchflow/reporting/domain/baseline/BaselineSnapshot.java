package com.dispatchflow.reporting.domain.baseline;

import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Baseline}'s whole state, which the repository stores and {@link Baseline#restore}
 * rebuilds. It holds no personal data: IDs, windows, times and figures only (NFR-152, ADR-109).
 *
 * @param plan the plan it is the baseline of, which keys it
 * @param version the version it was loaded at, or null for an aggregate never saved
 *     (ADR-126)
 * @param result the nearest-neighbour solve's figures
 * @param cause why it was last computed
 * @param computedAt when it was last computed
 */
public record BaselineSnapshot(
        PlanId plan, @Nullable Version version, PlannedFigures result, BaselineCause cause, Instant computedAt) {

    /** Checks every part is present. */
    public BaselineSnapshot {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(computedAt, "computedAt");
    }
}
