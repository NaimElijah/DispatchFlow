package com.dispatchflow.reporting.adapter.out.persistence;

import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.reporting.application.port.out.RefusalCause;
import com.dispatchflow.reporting.domain.baseline.Baseline;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps baselines in {@code reporting.baseline}, with their planned orders, withdrawn vehicles and
 * added orders in a table each, and every plan's settlement, computed or refused, in {@code
 * reporting.baseline_outcome}.
 *
 * <p>A plan is settled before its baseline is stored, by a statement that inserts nothing when the
 * plan is settled already, by a baseline or by a refusal. A concurrent settlement of the same plan
 * waits on the first one's row until that commits, and then finds it; either way the later call
 * ends in {@code StaleVersion}, and its retry finds the plan settled.
 */
@Component
@Transactional
class JpaBaselineRepository implements BaselineRepository {

    // The entity's name, which the shared save also gives a conflict as its kind.
    private static final String KIND = Baseline.class.getSimpleName();

    private final Baselines baselines;
    private final BaselineOutcomes outcomes;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param baselines the Spring Data interface of the baselines
     * @param outcomes the Spring Data interface of the plans' settlements
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaBaselineRepository(Baselines baselines, BaselineOutcomes outcomes, VersionedSave versionedSave) {
        this.baselines = Objects.requireNonNull(baselines, "baselines");
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public Optional<Baseline> get(PlanId plan) {
        UUID id = plan.value();
        Optional<Baseline> baseline = baselines.findById(id);
        if (baseline.isPresent()) {
            baselines.fetchWithdrawn(id);
            baselines.fetchAdded(id);
        }
        return baseline;
    }

    /**
     * {@inheritDoc}
     *
     * <p>It settles the plan first, and only then stores the baseline, through the shared save.
     */
    @Override
    public void add(Baseline baseline) {
        UUID plan = baseline.plan().value();
        if (outcomes.settleAsComputed(plan) == 0) {
            throw new StaleVersion(KIND, plan);
        }
        versionedSave.save(baseline, baselines::save);
    }

    @Override
    public void save(Baseline baseline) {
        versionedSave.save(baseline, baselines::save);
    }

    @Override
    public void recordRefusal(PlanId plan, RefusalCause cause) {
        if (outcomes.settleAsRefused(plan.value(), cause.name()) == 0) {
            throw new StaleVersion(KIND, plan.value());
        }
    }

    @Override
    public Optional<RefusalCause> refusalOf(PlanId plan) {
        return outcomes.findById(plan.value()).flatMap(StoredBaselineOutcome::refusal);
    }
}
