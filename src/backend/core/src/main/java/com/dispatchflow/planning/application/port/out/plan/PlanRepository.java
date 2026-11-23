package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.plan.PlansOnDate;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Optional;

/**
 * Where plans are kept, whole: a plan with its routes, stops, drops, pins, violations and journal,
 * never its past revisions (DL §3.6, §4.4 "What {@code Plan} loads", ADR-059). The persistence
 * adapter loads a plan whole, which its {@code @PostLoad} check checks (ADR-126), appends its
 * pending revision as rows when it saves it, and stores the events it raised with the change, in
 * the same transaction (DL §3.6, ADR-064). A partial unique index on the date backs one uncancelled plan per date (BR-34).
 *
 * <p>Built in M0, M5, M8 and M9, method by method.
 */
public interface PlanRepository {

    /**
     * One plan.
     *
     * <p>Built in M0.
     *
     * @param id the plan
     * @return the plan, or empty when no plan has the ID
     */
    Optional<Plan> get(PlanId id);

    /**
     * Adds a newly opened plan, in the caller's transaction.
     *
     * <p>Built in M0.
     *
     * @param plan the plan, never saved
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanAlreadyExists} when
     *     the unique index finds another uncancelled plan for the date, a race the read missed
     *     (BR-34)
     */
    void add(Plan plan);

    /**
     * Saves a changed plan with its version check, appends its pending revision, and stores the
     * events it raised, in the caller's transaction (BR-76, ADR-057, ADR-059).
     *
     * <p>Built in M0.
     *
     * @param plan the plan, at the version it was loaded at
     * @throws StaleVersion when the plan changed since it was loaded
     */
    void save(Plan plan);

    /**
     * The date's uncancelled plan, which a listener on an order's event and Live Operations' calls
     * change (FR-207, FR-315, FR-316, FR-404).
     *
     * <p>Built in M8, with its first user, {@code RemoveSupersededPutBackService} (FR-404); the M9
     * listeners reuse it.
     *
     * @param date the date
     * @return the plan, or empty when the date has none
     */
    Optional<Plan> uncancelledOn(DeliveryDate date);

    /**
     * The date's uncancelled plan, if it has one, which opening a plan judges (BR-34, ADR-101).
     *
     * <p>Built in M0.
     *
     * @param date the date
     * @return the date's plans as {@code Plan.open} takes them
     */
    PlansOnDate plansOn(DeliveryDate date);

    /**
     * The ready or published plans with a route not yet checked, which the re-check job evaluates
     * (FR-304, FR-395, NFR-42, ADR-058).
     *
     * <p>Built in M9.
     *
     * @return the plans, each once
     */
    List<PlanId> withUncheckedRoutes();

    /**
     * The date's plan that the midnight job completes or abandons: draft, solving, ready or published
     * (BR-102, BR-127). There is at most one, because a date has at most one uncancelled plan
     * (BR-34).
     *
     * <p>Built in M5.
     *
     * @param date the date that just ended
     * @return the plan, or empty when the date has none left to close
     */
    Optional<PlanId> unfinishedOn(DeliveryDate date);
}
