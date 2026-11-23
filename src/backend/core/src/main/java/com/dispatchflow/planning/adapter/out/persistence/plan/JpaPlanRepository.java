package com.dispatchflow.planning.adapter.out.persistence.plan;

import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.domain.PlanningError.PlanAlreadyExists;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.plan.PlansOnDate;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps plans in {@code planning.plan}, with their routes and stops as rows of their own, a table
 * each for their drops, excluded vehicles, pins, violations and both journals, and their revisions
 * appended to {@code planning.plan_revision} with their routes and stops.
 *
 * <p>Two openings of one date that race past the use case's read are put in order by the date's
 * advisory lock (ADR-127 decision 9): {@code add} takes it, then reads the date's uncancelled plan,
 * and answers with {@link PlanAlreadyExists} naming it. The partial unique index on a date's
 * uncancelled plan stays as the backstop for a write that skipped the lock (BR-34), and its
 * violation passes through as the bug it would be.
 */
@Component
@Transactional
class JpaPlanRepository implements PlanRepository {

    private final Plans plans;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param plans the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which {@code add} flushes and
     *     {@code save} appends a revision through
     */
    JpaPlanRepository(Plans plans, VersionedSave versionedSave, EntityManager entityManager) {
        this.plans = Objects.requireNonNull(plans, "plans");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<Plan> get(PlanId id) {
        return load(id.value());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The date's advisory lock comes first, so a second opening waits until the first commits
     * and then finds its plan. A plan is added as it was opened, in draft, so it has no revision to
     * append. It flushes at once, so a constraint the insert breaks fails inside this call.
     */
    @Override
    public void add(Plan plan) {
        LocalDate date = plan.date().value();
        plans.lockDate(date.toString());
        Optional<UUID> existing = plans.uncancelledOn(date);
        if (existing.isPresent()) {
            throw new RuleViolation(new PlanAlreadyExists(plan.date(), PlanId.of(existing.get())));
        }
        plans.save(plan);
        entityManager.flush();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The plan goes through the shared save before its pending revision is persisted. Hibernate
     * flushes inserts before updates, so a revision persisted first would reach the database ahead
     * of the version check: of two transactions revising one loaded version, the later one's insert
     * would then break the revision's key instead of answering {@code StaleVersion}. Once the save
     * has passed, this transaction holds the plan's row, and the revision is flushed at once, so a
     * failure still surfaces here. A loaded plan has no revision until a change makes one, and each
     * change replaces it, so a revision is appended once.
     */
    @Override
    public void save(Plan plan) {
        versionedSave.save(plan, plans::save);
        plan.pendingRevision().ifPresent(revision -> {
            entityManager.persist(new StoredPlanRevision(plan.id(), revision));
            entityManager.flush();
        });
    }

    @Override
    public Optional<Plan> uncancelledOn(DeliveryDate date) {
        return plans.uncancelledOn(date.value()).flatMap(this::load);
    }

    @Override
    public PlansOnDate plansOn(DeliveryDate date) {
        return new PlansOnDate(plans.uncancelledOn(date.value()).map(PlanId::of));
    }

    @Override
    public List<PlanId> withUncheckedRoutes() {
        return plans.withUncheckedRoutes().stream().map(PlanId::of).toList();
    }

    @Override
    public Optional<PlanId> unfinishedOn(DeliveryDate date) {
        return plans.unfinishedOn(date.value()).map(PlanId::of);
    }

    /**
     * Loads a plan whole: its routes and their stops with it, then each other collection into the
     * same instance. Its past revisions are never loaded.
     */
    private Optional<Plan> load(UUID id) {
        Optional<Plan> plan = plans.findById(id);
        if (plan.isPresent()) {
            plans.fetchStops(id);
            plans.fetchDropped(id);
            plans.fetchExcluded(id);
            plans.fetchPins(id);
            plans.fetchViolations(id);
            plans.fetchUndo(id);
            plans.fetchRedo(id);
        }
        return plan;
    }
}
