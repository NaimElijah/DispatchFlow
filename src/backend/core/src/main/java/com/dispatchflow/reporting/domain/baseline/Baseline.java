package com.dispatchflow.reporting.domain.baseline;

import com.dispatchflow.reporting.domain.CancelledOrders;
import com.dispatchflow.reporting.domain.PlannedOnTimeRate;
import com.dispatchflow.reporting.domain.PlannedOrder;
import com.dispatchflow.reporting.domain.PlannedOrders;
import com.dispatchflow.reporting.domain.PlannedService;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The nearest-neighbour plan a published plan is compared with (DL §4.7, §17 "Baseline"; FR-39),
 * keyed by the plan's ID. One exists per published plan, computed by Reporting's listener on {@code
 * PlanPublished} from a solve that obeys the business rules (FR-342); the key makes a second one
 * impossible. It is computed again when a revision withdraws a vehicle mid-route or adds orders, so
 * that both sides cover the same orders and fleet (FR-39, FR-267 to FR-271, ADR-109). Once a vehicle
 * is withdrawn, the comparison is on kilometres and on-time only (FR-269).
 *
 * <p>It holds no personal data, so Reporting has no retention period for it: it stays as long as its
 * plan, and its figures outlive any person's data (NFR-152, ADR-109). No method raises an event (DL
 * §7.2).
 *
 * <p>It is mapped for JPA (ADR-126, ADR-127): its key is the plan's UUID (ADR-127 decision 5); its
 * figures are two columns and an ordered collection of planned orders, each stored as a service kind
 * beside a start only a served order has (ADR-127 decision 3); and its cause is a kind beside two
 * ordered collections, the withdrawn vehicles and the added orders, both empty for a published one.
 * Every command changes these in place and ends by checking the invariants at rest.
 */
@Entity
@Table(schema = "reporting", name = "baseline")
public class Baseline extends AggregateRoot {

    /** Which {@link BaselineCause} a baseline has. */
    private enum CauseKind {
        PUBLISHED,
        RECOMPUTED
    }

    /** Which {@link PlannedService} a planned order has. */
    private enum ServiceKind {
        SERVED,
        DROPPED
    }

    /**
     * One {@link PlannedOrder} as its row stores it: the sealed {@link PlannedService} becomes a kind
     * and a start that only a served order has (ADR-127 decision 3). The public record is what every
     * caller sees; this class exists only for Hibernate.
     */
    @Embeddable
    private static class StoredPlannedOrder {

        // PostgreSQL reserves "order".
        @Column(name = "order_id")
        private OrderId order;

        @Embedded
        private TimeWindow window;

        @Enumerated(EnumType.STRING)
        private ServiceKind service;

        @Embedded
        @AttributeOverride(name = "date", column = @Column(name = "start_date"))
        @AttributeOverride(name = "seconds", column = @Column(name = "start_seconds"))
        private @Nullable DaySeconds start;

        /** For Hibernate, which sets the fields from the stored row. */
        protected StoredPlannedOrder() {}

        private StoredPlannedOrder(PlannedOrder planned) {
            this.order = planned.order();
            this.window = planned.window();
            switch (planned.service()) {
                case PlannedService.Served(DaySeconds plannedStart) -> {
                    this.service = ServiceKind.SERVED;
                    this.start = plannedStart;
                }
                case PlannedService.Dropped dropped -> this.service = ServiceKind.DROPPED;
            }
        }

        private PlannedOrder toPlannedOrder() {
            PlannedService planned = switch (service) {
                case SERVED -> new PlannedService.Served(Objects.requireNonNull(start, "start"));
                case DROPPED -> new PlannedService.Dropped();
            };
            return new PlannedOrder(order, window, planned);
        }
    }

    @Id
    private UUID id;

    private Distance distance;

    private DriverHours driverHours;

    @ElementCollection
    @CollectionTable(
            schema = "reporting",
            name = "baseline_planned_order",
            joinColumns = @JoinColumn(name = "baseline_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredPlannedOrder> plannedOrders = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private CauseKind cause;

    @ElementCollection
    @CollectionTable(
            schema = "reporting",
            name = "baseline_withdrawal",
            joinColumns = @JoinColumn(name = "baseline_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<Withdrawal> withdrawn = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "reporting",
            name = "baseline_added_order",
            joinColumns = @JoinColumn(name = "baseline_id"))
    @OrderColumn(name = "position", nullable = false)
    @Column(name = "order_id")
    private List<OrderId> added = new ArrayList<>();

    private Instant computedAt;

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected Baseline() {}

    private Baseline(PlanId plan, PlannedFigures result, Instant now) {
        super();
        this.id = plan.value();
        this.cause = CauseKind.PUBLISHED;
        this.computedAt = now;
        storeResult(result);
    }

    /**
     * Computes a published plan's baseline (FR-39, FR-342). The listener on {@code PlanPublished}
     * calls it once per plan; a repeated delivery finds the baseline already stored.
     *
     * @param plan the published plan, which keys the baseline
     * @param result the nearest-neighbour solve's figures, on the plan's orders and fleet
     * @param now when it was computed
     * @return the baseline, caused by the publication, never saved
     */
    public static Baseline compute(PlanId plan, PlannedFigures result, Instant now) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(now, "now");
        return new Baseline(plan, result, now);
    }

    /**
     * Checks the baseline as Hibernate loaded it (ADR-126 decision 8): its planned orders rebuild,
     * each order once; a recomputed baseline names at least one withdrawn vehicle or added order,
     * and each one once; and a published baseline names neither. It refuses a gap in a stored list
     * before it rebuilds the planned orders, which would read every element.
     *
     * @throws StoredStateInvalid when a stored list has a gap in its positions, or the stored state
     *     breaks one of them
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(Baseline.class, broken);
        });
        StoredStateInvalid.whileRestoring(PlannedOrders.class, this::orders);
    }

    /**
     * Takes a new solve after a revision withdrew vehicles mid-route, added orders, or both (FR-39,
     * FR-267, FR-270, ADR-109). The listener on {@code PlanRevised} calls it with what the revision
     * changed, and a solve on the plan's orders that treats the withdrawn vehicles as {@link
     * #withdrawnFleetAfter} splits them (FR-271).
     *
     * <p>What changed adds to what was named before, in the order first named, because a withdrawn
     * route never returns (FR-268) and an added order stays in the comparison; something named again
     * is named once, a vehicle with the cause it was first named with. A revision that neither
     * withdrew a vehicle nor added an order changes nothing, so a recompute asked for without either
     * is ignored. A cancellation is neither: it only leaves the order out of both planned on-time
     * rates (BR-30, ADR-109).
     *
     * @param withdrawn every vehicle this revision withdrew mid-route, with its cause (FR-267,
     *     FR-271), in the event's order; empty when it withdrew none
     * @param added every order this revision brought into the plan that the plan didn't hold before,
     *     in the event's order; empty when it added none. An order that moved between the plan's
     *     routes, or came back from *dropped*, was already the plan's, so it is not added
     * @param newResult the new solve's figures, on every order the plan now holds, the added ones
     *     included
     * @param now when it was computed
     */
    public void recompute(List<Withdrawal> withdrawn, List<OrderId> added, PlannedFigures newResult, Instant now) {
        Objects.requireNonNull(withdrawn, "withdrawn");
        Objects.requireNonNull(added, "added");
        Objects.requireNonNull(newResult, "newResult");
        Objects.requireNonNull(now, "now");
        if (withdrawn.isEmpty() && added.isEmpty()) {
            return;
        }
        List<Withdrawal> nextWithdrawn = withdrawalsWith(this.withdrawn, withdrawn);
        List<OrderId> nextAdded = union(this.added, added);
        storeResult(newResult);
        cause = CauseKind.RECOMPUTED;
        // Both lists keep their stored items first and in order, and are refilled in place, so
        // Hibernate saves the new items alone, as inserts: V11 lets app neither update nor delete a
        // row of baseline_withdrawal or baseline_added_order.
        replace(this.withdrawn, nextWithdrawn);
        replace(this.added, nextAdded);
        computedAt = now;
        requireInvariants();
    }

    /**
     * Sets a plan against this baseline (FR-39). A cancelled plan is not compared (FR-338). Once a
     * vehicle was withdrawn, the comparison is on kilometres and planned on-time only, and names the
     * withdrawn vehicles, because their routes' driver-hours are unknown (FR-268, FR-269); added
     * orders alone keep every measure. Both planned on-time rates leave out the orders cancelled now,
     * including those cancelled after the baseline was computed (BR-30). Nothing changes.
     *
     * @param standing where the plan is now
     * @param planFigures the plan's current figures
     * @param cancelled the orders of the date cancelled now
     * @return the comparison
     */
    public BaselineComparison compareWith(
            PlanStanding standing, PlannedFigures planFigures, CancelledOrders cancelled) {
        Objects.requireNonNull(standing, "standing");
        Objects.requireNonNull(planFigures, "planFigures");
        Objects.requireNonNull(cancelled, "cancelled");
        if (standing == PlanStanding.CANCELLED) {
            return new BaselineComparison.NotCompared();
        }
        PlannedFigures result = result();
        PlannedOnTimeRate planOnTime = planFigures.plannedOnTime(cancelled);
        PlannedOnTimeRate baselineOnTime = result.plannedOnTime(cancelled);
        List<VehicleId> withdrawnVehicles =
                withdrawn.stream().map(Withdrawal::vehicle).toList();
        if (withdrawnVehicles.isEmpty()) {
            return new BaselineComparison.AllMeasures(
                    planFigures.distance(),
                    result.distance(),
                    planFigures.driverHours(),
                    result.driverHours(),
                    planOnTime,
                    baselineOnTime);
        }
        return new BaselineComparison.KilometresAndOnTime(
                planFigures.distance(), result.distance(), planOnTime, baselineOnTime, withdrawnVehicles);
    }

    /**
     * The withdrawn vehicles the solve for the next {@link #recompute} treats, split as FR-271 says:
     * every vehicle withdrawn so far and the ones a revision withdraws now, because each withdrawn
     * route stays withdrawn (FR-268). The listener asks for that solve before it recomputes. Nothing
     * changes.
     *
     * @param withdrawn the vehicles the revision withdrew, with their causes; empty when it withdrew
     *     none
     * @return the vehicles to leave out and those to give their whole shift
     */
    public WithdrawnFleet withdrawnFleetAfter(List<Withdrawal> withdrawn) {
        Objects.requireNonNull(withdrawn, "withdrawn");
        return WithdrawnFleet.of(withdrawalsWith(this.withdrawn, withdrawn));
    }

    /**
     * The plan this baseline is of.
     *
     * @return the plan's ID, which keys the baseline
     */
    public PlanId plan() {
        return PlanId.of(id);
    }

    /**
     * The figures of the nearest-neighbour solve it was last computed from (FR-39, FR-342).
     *
     * @return the distance, the driver-hours and every order's planned service
     */
    public PlannedFigures result() {
        return new PlannedFigures(distance, driverHours, orders());
    }

    private PlannedOrders orders() {
        return PlannedOrders.of(
                plannedOrders.stream().map(StoredPlannedOrder::toPlannedOrder).toList());
    }

    /**
     * Why it was last computed, which decides what it is compared on (FR-269).
     *
     * @return published, or recomputed with unmodifiable copies of the withdrawn vehicles and the
     *     added orders, each in the order first named
     */
    public BaselineCause cause() {
        return switch (cause) {
            case PUBLISHED -> new BaselineCause.Published();
            case RECOMPUTED -> new BaselineCause.Recomputed(withdrawn, added);
        };
    }

    /**
     * When it was last computed.
     *
     * @return the time of its computation or of its latest recompute
     */
    public Instant computedAt() {
        return computedAt;
    }

    /** Sets the figures, changing the planned orders Hibernate loaded in place. */
    private void storeResult(PlannedFigures result) {
        distance = result.distance();
        driverHours = result.driverHours();
        replace(
                plannedOrders,
                result.orders().orders().stream().map(StoredPlannedOrder::new).toList());
    }

    /** Swaps a mapped collection's contents, never the collection Hibernate loaded (ADR-126). */
    private static <T> void replace(List<T> mapped, List<T> next) {
        mapped.clear();
        mapped.addAll(next);
    }

    /**
     * The withdrawals named so far, first and in their order, then the new ones, each vehicle once,
     * with its first cause.
     */
    private static List<Withdrawal> withdrawalsWith(List<Withdrawal> named, List<Withdrawal> more) {
        List<Withdrawal> all = new ArrayList<>(named);
        for (Withdrawal withdrawal : more) {
            if (all.stream().noneMatch(each -> each.vehicle().equals(withdrawal.vehicle()))) {
                all.add(withdrawal);
            }
        }
        return all;
    }

    /** The items named so far, first and in their order, then the new ones, each once. */
    private static <T> List<T> union(List<T> named, List<T> more) {
        List<T> all = new ArrayList<>(named);
        more.stream().filter(item -> !all.contains(item)).forEach(all::add);
        return all;
    }

    /**
     * Checks the state a command left against the invariants at rest (ADR-126 decision 11). Every
     * input was checked before this point, so a broken invariant here is a bug in the command, not a
     * refusal, and the error rolls its transaction back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a baseline command left a state that breaks: " + broken);
        });
    }

    /** The one check of the stored kind and collections, shared by the load check and every command. */
    private Optional<String> brokenRule() {
        if (StoredLists.anyHasGap(plannedOrders, withdrawn, added)) {
            return Optional.of(StoredLists.GAP);
        }
        if (cause == CauseKind.PUBLISHED && !(withdrawn.isEmpty() && added.isEmpty())) {
            return Optional.of("a published baseline names no withdrawn vehicle or added order");
        }
        return brokenBy(cause());
    }

    /** The invariant a cause holds at rest, which the load check and the end check apply. */
    private static Optional<String> brokenBy(BaselineCause cause) {
        if (cause instanceof BaselineCause.Recomputed(List<Withdrawal> withdrawn, List<OrderId> added)) {
            if (withdrawn.isEmpty() && added.isEmpty()) {
                return Optional.of("a recomputed baseline names a withdrawn vehicle or an added order");
            }
            long vehicles =
                    withdrawn.stream().map(Withdrawal::vehicle).distinct().count();
            if (vehicles != withdrawn.size() || new HashSet<>(added).size() != added.size()) {
                return Optional.of("a baseline names each withdrawn vehicle and added order once");
            }
        }
        return Optional.empty();
    }
}
