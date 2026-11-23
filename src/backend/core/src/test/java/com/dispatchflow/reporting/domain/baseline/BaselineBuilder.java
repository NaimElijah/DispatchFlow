package com.dispatchflow.reporting.domain.baseline;

import static com.dispatchflow.reporting.domain.ReportingFixtures.BASELINE_FIGURES;
import static com.dispatchflow.reporting.domain.ReportingFixtures.PLAN;
import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.reporting.domain.ReportingFixtures;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;

/**
 * The test builder of {@link Baseline}: {@code aBaseline()} is a saved baseline of {@link
 * ReportingFixtures#PLAN}, computed when the plan was published. Every state is one the commands
 * reach (ADR-127 decision 2): a baseline that names withdrawn vehicles was computed at {@link
 * #PUBLISHED_AT} and recomputed once, at {@link #COMPUTED_AT}, from the figures given.
 * {@link #build} computes a new baseline each time and saves it, so one builder may build many.
 */
final class BaselineBuilder {

    /** The version {@link #build} leaves: a baseline's first save stores 0 (ADR-126). */
    static final Version LOADED = Version.of(0);

    /** When the built baseline was last computed: published then, or recomputed then. */
    static final Instant COMPUTED_AT = Instant.parse("2027-06-12T15:00:00Z");

    /** When a recomputed baseline was first computed, before {@link #COMPUTED_AT}. */
    static final Instant PUBLISHED_AT = Instant.parse("2027-06-12T14:00:00Z");

    private PlannedFigures result = BASELINE_FIGURES;
    private List<Withdrawal> withdrawn = List.of();

    private BaselineBuilder() {}

    static BaselineBuilder aBaseline() {
        return new BaselineBuilder();
    }

    /** Recomputed once, after a revision withdrew these vehicles, in this order. */
    BaselineBuilder withdrawn(Withdrawal... withdrawals) {
        this.withdrawn = List.of(withdrawals);
        return this;
    }

    /** The figures it was last computed from: the publication's, or the recompute's. */
    BaselineBuilder withResult(PlannedFigures result) {
        this.result = result;
        return this;
    }

    /** A new baseline computed, and recomputed when vehicles were withdrawn, then saved. */
    Baseline build() {
        if (withdrawn.isEmpty()) {
            return asSaved(Baseline.compute(PLAN, result, COMPUTED_AT));
        }
        Baseline baseline = Baseline.compute(PLAN, BASELINE_FIGURES, PUBLISHED_AT);
        baseline.recompute(withdrawn, List.of(), result, COMPUTED_AT);
        return asSaved(baseline);
    }
}
