package com.dispatchflow.reporting.adapter.out.persistence;

import com.dispatchflow.reporting.application.port.out.RefusalCause;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * How a plan's publication baseline was settled, a row written once and never changed (DL §3.2,
 * ADR-127 decision 6): computed, or refused by the optimizer with a cause (FR-39). It is keyed by
 * the plan, so a plan is settled once. The repository writes it with a native {@code INSERT … ON
 * CONFLICT DO NOTHING}, so a second settlement of a plan adds nothing and is told apart from the
 * first.
 */
@Entity
@Immutable
@Table(schema = "reporting", name = "baseline_outcome")
class StoredBaselineOutcome {

    /** How a plan was settled; a computed one has its {@code Baseline} row beside this one. */
    enum Outcome {
        /** A baseline was computed and stored. */
        COMPUTED,
        /** The optimizer refused the baseline, for the row's refusal cause. */
        REFUSED
    }

    @Id
    @Column(name = "plan_id")
    private UUID plan;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    @Enumerated(EnumType.STRING)
    private @Nullable RefusalCause refusalCause;

    /** For Hibernate. */
    protected StoredBaselineOutcome() {}

    /**
     * The refusal this row records.
     *
     * @return the optimizer's cause, or empty when the plan's baseline was computed
     */
    Optional<RefusalCause> refusal() {
        return Optional.ofNullable(refusalCause);
    }
}
