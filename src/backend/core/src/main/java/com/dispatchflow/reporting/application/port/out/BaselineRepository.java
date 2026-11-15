package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.Baseline;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * Where baselines are kept, as whole {@link Baseline} aggregates keyed by their plan (DL §3.6,
 * §4.7, FR-39). A baseline holds no personal data, so it stays as long as its plan (NFR-152,
 * ADR-109).
 *
 * <p>Built in M5 and M9, method by method.
 */
public interface BaselineRepository {

    /**
     * Loads a plan's baseline.
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the baseline at its stored version, or empty while none was computed
     */
    Optional<Baseline> get(PlanId plan);

    /**
     * Stores a new baseline, in the caller's transaction. The plan keys it, so the store refuses a
     * second baseline of one plan (FR-39).
     *
     * <p>Built in M5.
     *
     * @param baseline the baseline, at {@code Version.NEW}
     */
    void add(Baseline baseline);

    /**
     * Stores a recomputed baseline, in the caller's transaction, if its stored version is still the
     * one it was loaded at (BR-76, ADR-057).
     *
     * <p>Built in M9.
     *
     * @param baseline the baseline
     * @throws StaleVersion when another recompute was saved since it was loaded
     */
    void save(Baseline baseline);

    /**
     * Records that the optimizer refused the baseline of a plan's publication, in the caller's
     * transaction, so a repeated delivery and a later recompute find the plan settled (FR-39). The
     * plan keys it, as it keys a baseline, so the store refuses a second record of one plan, and a
     * plan holds a baseline or a refusal, never both.
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @param cause why the optimizer found no baseline
     */
    void recordRefusal(PlanId plan, RefusalCause cause);

    /**
     * Loads the refusal recorded for a plan's publication baseline.
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the refusal's cause, or empty when none was recorded
     */
    Optional<RefusalCause> refusalOf(PlanId plan);
}
