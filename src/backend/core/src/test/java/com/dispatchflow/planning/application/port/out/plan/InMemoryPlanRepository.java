package com.dispatchflow.planning.application.port.out.plan;

import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.planning.domain.PlanningError.PlanAlreadyExists;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.plan.PlansOnDate;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Planning's plans, in memory, for application tests. It keeps the instances it is given, and does
 * to them what the JPA adapter's writes do: {@link #add} refuses a second uncancelled plan on a date
 * with {@code PlanAlreadyExists} naming the first, as the date's lock makes it (ADR-127 decision 9),
 * and {@link #save} refuses a plan whose version moved since it was loaded, as {@code VersionedSave}
 * does. Both publish the plan's events through {@code SaveEvents.publish} and move its version, by
 * {@link com.dispatchflow.shared.AsSaved#asSaved}.
 */
public final class InMemoryPlanRepository implements PlanRepository {

    private static final String KIND = "Plan";

    private final Map<PlanId, Plan> plans = new LinkedHashMap<>();
    private final Map<PlanId, Version> storedVersions = new HashMap<>();

    /**
     * Keeps a plan a test built and saved, such as one of {@code PlanningFixtures.aPlan()}'s, at the
     * version it was saved at.
     *
     * @param saved the plan, saved at least once
     * @return this repository
     * @throws IllegalArgumentException when the plan was never saved
     */
    public InMemoryPlanRepository holding(Plan saved) {
        Version version = saved.savedVersion()
                .orElseThrow(() -> new IllegalArgumentException("a plan held must have been saved"));
        plans.put(saved.id(), saved);
        storedVersions.put(saved.id(), version);
        return this;
    }

    /**
     * Every plan kept.
     *
     * @return the plans, in the order they were first kept
     */
    public List<Plan> all() {
        return List.copyOf(plans.values());
    }

    @Override
    public Optional<Plan> get(PlanId id) {
        return Optional.ofNullable(plans.get(id));
    }

    @Override
    public void add(Plan plan) {
        if (plan.savedVersion().isPresent()) {
            throw new IllegalArgumentException("add takes a plan never saved");
        }
        Optional<PlanId> existing = uncancelledId(plan.date());
        if (existing.isPresent()) {
            throw new RuleViolation(new PlanAlreadyExists(plan.date(), existing.get()));
        }
        asSaved(plan);
        plans.put(plan.id(), plan);
        storedVersions.put(plan.id(), plan.version());
    }

    @Override
    public void save(Plan plan) {
        Version stored = storedVersions.get(plan.id());
        if (stored == null) {
            throw new IllegalStateException("save takes a plan that was added or held");
        }
        if (!plan.version().equals(stored)) {
            throw new StaleVersion(KIND, plan.id().value());
        }
        asSaved(plan);
        plans.put(plan.id(), plan);
        storedVersions.put(plan.id(), plan.version());
    }

    @Override
    public Optional<Plan> uncancelledOn(DeliveryDate date) {
        return uncancelledId(date).map(plans::get);
    }

    @Override
    public PlansOnDate plansOn(DeliveryDate date) {
        return new PlansOnDate(uncancelledId(date));
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public List<PlanId> withUncheckedRoutes() {
        throw new UnsupportedOperationException("the in-memory plans don't track unchecked routes");
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public Optional<PlanId> unfinishedOn(DeliveryDate date) {
        throw new UnsupportedOperationException("the in-memory plans don't find a date's unfinished plan");
    }

    private Optional<PlanId> uncancelledId(DeliveryDate date) {
        return plans.values().stream()
                .filter(plan -> plan.date().equals(date))
                .filter(plan -> !(plan.status() instanceof Plan.Status.Cancelled))
                .map(Plan::id)
                .findFirst();
    }
}
