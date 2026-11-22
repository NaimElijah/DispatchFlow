package com.dispatchflow.reporting.domain.baseline;

import static com.dispatchflow.reporting.domain.ReportingFixtures.BASELINE_FIGURES;
import static com.dispatchflow.reporting.domain.ReportingFixtures.PLAN;

import com.dispatchflow.reporting.domain.ReportingFixtures;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;

/**
 * The test builder of {@link Baseline}: {@code aBaseline()} is a loaded baseline of {@link
 * ReportingFixtures#PLAN}, computed when the plan was published. Every state is built as a snapshot
 * and rebuilt through {@code restore}.
 */
final class BaselineBuilder {

    static final Version LOADED = Version.of(3);
    static final Instant COMPUTED_AT = Instant.parse("2027-06-12T15:00:00Z");

    private PlannedFigures result = BASELINE_FIGURES;
    private BaselineCause cause = new BaselineCause.Published();

    private BaselineBuilder() {}

    static BaselineBuilder aBaseline() {
        return new BaselineBuilder();
    }

    BaselineBuilder withdrawn(Withdrawal... withdrawals) {
        this.cause = new BaselineCause.Recomputed(List.of(withdrawals), List.of());
        return this;
    }

    BaselineBuilder added(OrderId... orders) {
        this.cause = new BaselineCause.Recomputed(List.of(), List.of(orders));
        return this;
    }

    BaselineBuilder withCause(BaselineCause cause) {
        this.cause = cause;
        return this;
    }

    BaselineBuilder withResult(PlannedFigures result) {
        this.result = result;
        return this;
    }

    BaselineSnapshot snapshot() {
        return new BaselineSnapshot(PLAN, LOADED, result, cause, COMPUTED_AT);
    }

    Baseline build() {
        return Baseline.restore(snapshot());
    }
}
