package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.reconciliation.LateOutcome;
import com.dispatchflow.planning.domain.solution.StopPlan;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An order's place on a route, an entity inside {@link Plan} keyed by its {@link StopId} (DL §4.4,
 * §17 "Stop", ADR-126). Its ID stays the same while it moves between routes (DL §3.1): a move takes
 * this same instance off one route's list and puts it on another's. A stop that joined its route
 * through a late fact may have no planned times.
 *
 * <p>Only its plan changes it: a judged route gives it times, and a late fact marks how it joined.
 * The plan's own collection of stops creates and deletes it; its route's list holds only where it is.
 * Its planned times are stored in {@link StoredStopPlan}'s columns, all of them or none.
 */
@Entity
@Table(schema = "planning", name = "planned_stop")
class PlannedStop {

    @Id
    private UUID id;

    // PostgreSQL reserves "order".
    @Column(name = "order_id")
    private OrderId order;

    private Attempt attempt;

    @Embedded
    private @Nullable StoredStopPlan plan;

    @Enumerated(EnumType.STRING)
    private @Nullable LateOutcome late;

    /** For Hibernate, which sets the fields from the stored row. */
    protected PlannedStop() {}

    /**
     * Creates a stop.
     *
     * @param id its ID
     * @param order its order
     * @param attempt its attempt
     * @param plan its planned times, if it has them
     * @param late how it joined its route after a late fact, if it did
     */
    PlannedStop(StopId id, OrderId order, Attempt attempt, Optional<StopPlan> plan, Optional<LateOutcome> late) {
        this.id = id.value();
        this.order = order;
        this.attempt = attempt;
        this.plan = plan.map(StoredStopPlan::new).orElse(null);
        this.late = late.orElse(null);
    }

    /** Rebuilds a stop from a snapshot; it goes with {@code Plan.restore} (ADR-126). */
    static PlannedStop from(PlanSnapshot.StopSnapshot snapshot) {
        return new PlannedStop(snapshot.id(), snapshot.order(), snapshot.attempt(), snapshot.plan(), snapshot.late());
    }

    StopId id() {
        return StopId.of(id);
    }

    OrderId order() {
        return order;
    }

    Attempt attempt() {
        return attempt;
    }

    /**
     * Its planned times.
     *
     * @return the times, or empty when it has none
     * @throws com.dispatchflow.shared.StoredStateInvalid when its stored times are refused by their
     *     own rules
     */
    Optional<StopPlan> plan() {
        return Optional.ofNullable(plan).map(StoredStopPlan::stopPlan);
    }

    Optional<LateOutcome> late() {
        return Optional.ofNullable(late);
    }

    StopKey key() {
        return new StopKey(order, attempt);
    }

    /** Gives the stop the times a judged route has for it, or none when the validator couldn't time it. */
    void timeAs(Optional<StopPlan> next) {
        this.plan = next.map(StoredStopPlan::new).orElse(null);
    }

    /** Marks how the stop joined its route after a late fact, or that it didn't. */
    void markLate(Optional<LateOutcome> next) {
        this.late = next.orElse(null);
    }

    /** The stop as its plan's snapshot holds it; it goes with {@code Plan.snapshot} (ADR-126). */
    PlanSnapshot.StopSnapshot snapshot() {
        return new PlanSnapshot.StopSnapshot(id(), order, attempt, plan(), late());
    }
}
