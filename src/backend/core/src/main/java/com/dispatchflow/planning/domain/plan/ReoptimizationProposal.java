package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.PlanningError.DriverRoutedTwice;
import com.dispatchflow.planning.domain.PlanningError.OrderAlreadyInPlan;
import com.dispatchflow.planning.domain.PlanningError.ProposalAlreadyDeclined;
import com.dispatchflow.planning.domain.PlanningError.VehicleRoutedTwice;
import com.dispatchflow.planning.domain.basis.ProposalBasis;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.planning.domain.solution.DriverChange;
import com.dispatchflow.planning.domain.solution.LateStop;
import com.dispatchflow.planning.domain.solution.ObjectiveTerms;
import com.dispatchflow.planning.domain.solution.ReoptimizationResult;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * A re-optimization's result, waiting for a dispatcher (DL §4.4, §17 "Proposal"). It never changes,
 * except to be declined once; nothing reaches drivers until a dispatcher accepts it, and accepting
 * it is a revision of the plan that cites it (FR-255, BR-26, ADR-056). Its basis is what acceptance
 * judges the changes since against (BR-25, BR-82).
 *
 * <p>It is mapped for JPA (ADR-126, ADR-127): its key is its {@code ProposalId}'s UUID; its basis,
 * objective and state are columns of its row, the state as a kind beside who declined it and when;
 * and each list is a table of its own. Its solved routes' stops are flattened into one table keyed by
 * both positions, and so are its driver changes' orders, keyed by the change and the list they are
 * in (ADR-127 decision 3).
 */
@Entity
@Table(schema = "planning", name = "reoptimization_proposal")
public class ReoptimizationProposal extends AggregateRoot {

    private static final String ORDER_ONCE = "an order and attempt is served or dropped at most once";
    private static final String ONE_ROUTE_EACH = "a vehicle or driver drives at most one route";
    private static final String DECLINED_AFTER_CREATED = "a proposal is declined no earlier than it is created";

    /** Whether a dispatcher may still accept it (DL §4.4). */
    public sealed interface State {

        /** Waiting for a dispatcher. */
        record Ready() implements State {}

        /**
         * A dispatcher declined it; the plan stays as it was (BR-26).
         *
         * @param by who declined it
         * @param at when
         */
        record Declined(AccountId by, Instant at) implements State {

            /** Checks both parts are present. */
            public Declined {
                Objects.requireNonNull(by, "by");
                Objects.requireNonNull(at, "at");
            }
        }
    }

    /** Which {@link State} a proposal is in. */
    private enum StateKind {
        READY,
        DECLINED
    }

    /**
     * One order of a {@link DriverChange}'s three lists, as its row stores it: the change it belongs
     * to, by its position, and the list it is in. A list inside a list can't be an embeddable's, so
     * every change's orders are flattened into one table (ADR-127 decision 3). This class exists only
     * for Hibernate.
     */
    @Embeddable
    private static class StoredChangedOrder {

        /** The list of its change an order is in. */
        private enum Change {
            ADDED,
            REMOVED,
            REORDERED
        }

        private int driverChange;

        @Enumerated(EnumType.STRING)
        private Change change;

        // PostgreSQL reserves "order".
        @Column(name = "order_id")
        private OrderId order;

        /** For Hibernate, which sets the fields from the stored row. */
        protected StoredChangedOrder() {}

        private StoredChangedOrder(int driverChange, Change change, OrderId order) {
            this.driverChange = driverChange;
            this.change = change;
            this.order = order;
        }

        /** Stores each change's orders, the changes in order and each list in order. */
        private static void flatten(
                List<DriverChange> changes, List<AccountId> drivers, List<StoredChangedOrder> orders) {
            for (int c = 0; c < changes.size(); c++) {
                DriverChange driverChange = changes.get(c);
                drivers.add(driverChange.driver());
                for (OrderId added : driverChange.added()) {
                    orders.add(new StoredChangedOrder(c, Change.ADDED, added));
                }
                for (OrderId removed : driverChange.removed()) {
                    orders.add(new StoredChangedOrder(c, Change.REMOVED, removed));
                }
                for (OrderId reordered : driverChange.reordered()) {
                    orders.add(new StoredChangedOrder(c, Change.REORDERED, reordered));
                }
            }
        }

        /** The changes the stored drivers and orders hold, each list in its stored order. */
        private static List<DriverChange> assemble(List<AccountId> drivers, List<StoredChangedOrder> orders) {
            for (StoredChangedOrder order : orders) {
                if (order.driverChange < 0 || order.driverChange >= drivers.size()) {
                    throw new StoredStateInvalid(
                            DriverChange.class, "a changed order belongs to one of its proposal's driver changes");
                }
            }
            return IntStream.range(0, drivers.size())
                    .mapToObj(c -> new DriverChange(
                            drivers.get(c),
                            ordersOf(orders, c, Change.ADDED),
                            ordersOf(orders, c, Change.REMOVED),
                            ordersOf(orders, c, Change.REORDERED)))
                    .toList();
        }

        private static List<OrderId> ordersOf(List<StoredChangedOrder> orders, int driverChange, Change change) {
            return orders.stream()
                    .filter(order -> order.driverChange == driverChange && order.change == change)
                    .map(order -> order.order)
                    .toList();
        }
    }

    @Id
    private UUID id;

    @Column(name = "plan_id")
    private PlanId plan;

    @Column(name = "job_id")
    private JobId job;

    @Embedded
    @AttributeOverride(name = "revision", column = @Column(name = "basis_revision"))
    @AttributeOverride(name = "factsAt", column = @Column(name = "basis_facts_at"))
    private ProposalBasis basis;

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_route",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredSolvedRoute> routes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_stop",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredSolvedStop> stops = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_drop",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredDroppedOrder> dropped = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_excluded_vehicle",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<ExcludedVehicle> excluded = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_withdrawal",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<VehicleWithdrawal> withdrawn = new ArrayList<>();

    @Embedded
    @AttributeOverride(name = "travel", column = @Column(name = "objective_travel"))
    @AttributeOverride(name = "drops", column = @Column(name = "objective_drops"))
    @AttributeOverride(name = "doorWindow", column = @Column(name = "objective_door_window"))
    @AttributeOverride(name = "fixedVehicles", column = @Column(name = "objective_fixed_vehicles"))
    @AttributeOverride(name = "lateness", column = @Column(name = "objective_lateness"))
    @AttributeOverride(name = "orphanedStops", column = @Column(name = "objective_orphaned_stops"))
    @AttributeOverride(name = "movedStops", column = @Column(name = "objective_moved_stops"))
    private ObjectiveTerms objective;

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_late_stop",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<LateStop> lateStops = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_vehicle_without_position",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    @Column(name = "vehicle_id")
    private List<VehicleId> withoutUsablePosition = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_driver_change",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    @Column(name = "driver_id")
    private List<AccountId> changedDrivers = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_changed_order",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredChangedOrder> changedOrders = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            schema = "planning",
            name = "reoptimization_proposal_goods_hand_over",
            joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position", nullable = false)
    @Column(name = "order_id")
    private List<OrderId> needsGoodsHandOver = new ArrayList<>();

    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private StateKind state;

    private @Nullable AccountId declinedBy;

    private @Nullable Instant declinedAt;

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected ReoptimizationProposal() {}

    private ReoptimizationProposal(
            ProposalId id, PlanId plan, JobId job, ProposalBasis basis, ReoptimizationResult result, Instant now) {
        super();
        this.id = id.value();
        this.plan = plan;
        this.job = job;
        this.basis = basis;
        StoredSolvedRoute.flatten(result.solution().routes(), routes, stops);
        result.solution().dropped().stream().map(StoredDroppedOrder::new).forEach(dropped::add);
        excluded.addAll(result.solution().excluded());
        withdrawn.addAll(result.withdrawn());
        this.objective = result.objective();
        lateStops.addAll(result.lateStops());
        withoutUsablePosition.addAll(result.withoutUsablePosition());
        StoredChangedOrder.flatten(result.driverChanges(), changedDrivers, changedOrders);
        needsGoodsHandOver.addAll(result.needsGoodsHandOver());
        this.createdAt = now;
        storeState(new State.Ready());
    }

    /**
     * Creates a proposal; {@link SolveJob#succeed(ProposalId, ReoptimizationResult, Instant)} calls it
     * in the same transaction as the job's success (DL §6.1).
     *
     * @param id the new proposal's ID
     * @param plan the plan it would change
     * @param job the job that computed it
     * @param basis the revision and fact time it was computed from
     * @param result the validated solution and the preview's facts
     * @param now when it was created
     * @return the proposal, ready
     * @throws RuleViolation with what its load check would refuse, so no stored proposal fails to
     *     load: {@link OrderAlreadyInPlan} for an order and attempt served or dropped twice (BR-07),
     *     {@link VehicleRoutedTwice} for a vehicle with two routes, withdrawn or not, or {@link
     *     DriverRoutedTwice} for a driver on two routes it doesn't withdraw (BR-39, ADR-101)
     */
    static ReoptimizationProposal create(
            ProposalId id, PlanId plan, JobId job, ProposalBasis basis, ReoptimizationResult result, Instant now) {
        Optional<PlanningError> broken = SolutionShape.brokenBy(
                result.solution().routes(), result.solution().dropped(), result.withdrawn());
        if (broken.isPresent()) {
            throw new RuleViolation(broken.get());
        }
        return new ReoptimizationProposal(id, plan, job, basis, result, now);
    }

    /**
     * Checks the proposal as Hibernate loaded it (ADR-126 decision 8): an order and attempt served or
     * dropped at most once (BR-07), a vehicle on at most one route and a driver on at most one route
     * it isn't withdrawn from (BR-39), and a decline no earlier than its creation, after rebuilding
     * every list it stores.
     *
     * @throws StoredStateInvalid when the stored state breaks one of them, or a stored value is
     *     refused
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(ReoptimizationProposal.class, broken);
        });
    }

    /**
     * A dispatcher declines the proposal; nothing else changes (BR-26, DL §6.2). An accepted proposal
     * may still be declined: acceptance lives on the plan, so this changes nothing it applied
     * (ADR-101). A {@code now} before the proposal was created counts as its creation (ADR-112).
     *
     * @param by who declines it
     * @param now when
     * @throws RuleViolation with {@link ProposalAlreadyDeclined} when it was declined before
     */
    public void decline(AccountId by, Instant now) {
        if (state == StateKind.DECLINED) {
            throw new RuleViolation(new ProposalAlreadyDeclined(id()));
        }
        storeState(new State.Declined(by, TimeOrder.notBefore(now, createdAt)));
        requireInvariants();
    }

    /**
     * The proposal's ID.
     *
     * @return the ID
     */
    public ProposalId id() {
        return ProposalId.of(id);
    }

    /**
     * The plan it would change, which {@link Plan#accept} checks is the plan accepting it.
     *
     * @return the plan's ID
     */
    public PlanId plan() {
        return plan;
    }

    /**
     * The revision and fact time it was computed from, which acceptance judges the changes since
     * against (BR-25, BR-82).
     *
     * @return the basis
     */
    public ProposalBasis basis() {
        return basis;
    }

    /**
     * Whether a dispatcher may still accept it.
     *
     * @return ready, or declined with who declined it and when
     */
    public State state() {
        return switch (state) {
            case READY -> new State.Ready();
            case DECLINED ->
                new State.Declined(
                        Objects.requireNonNull(declinedBy, "a declined proposal has who declined it"),
                        Objects.requireNonNull(declinedAt, "a declined proposal has when"));
        };
    }

    /**
     * Its routes, keyed by vehicle and order, not yet by the plan's IDs.
     *
     * @return the routes, in order, each with its stops in order
     */
    public List<SolvedRoute> routes() {
        return StoredSolvedRoute.assemble(routes, stops);
    }

    /**
     * Its drops.
     *
     * @return the drops, in order
     */
    public List<DroppedOrder> dropped() {
        return dropped.stream().map(StoredDroppedOrder::droppedOrder).toList();
    }

    /**
     * The vehicles it left out, each with its cause (FR-197, ADR-112).
     *
     * @return an unmodifiable copy, in order
     */
    public List<ExcludedVehicle> excluded() {
        return List.copyOf(excluded);
    }

    /**
     * The vehicles it withdraws mid-route (FR-267).
     *
     * @return an unmodifiable copy, in order
     */
    public List<VehicleWithdrawal> withdrawn() {
        return List.copyOf(withdrawn);
    }

    /**
     * The job that computed it.
     *
     * @return the job's ID
     */
    public JobId job() {
        return job;
    }

    /**
     * Its objective, term by term, for the preview's change in totals (FR-103).
     *
     * @return the terms
     */
    public ObjectiveTerms objective() {
        return objective;
    }

    /**
     * The stops it keeps past their window end (BR-107).
     *
     * @return an unmodifiable copy, in order
     */
    public List<LateStop> lateStops() {
        return List.copyOf(lateStops);
    }

    /**
     * The vehicles with no usable position (BR-81).
     *
     * @return an unmodifiable copy, in order
     */
    public List<VehicleId> withoutUsablePosition() {
        return List.copyOf(withoutUsablePosition);
    }

    /**
     * What changes for each driver (FR-36).
     *
     * @return the changes, in order, each with its three lists in order
     */
    public List<DriverChange> driverChanges() {
        return StoredChangedOrder.assemble(changedDrivers, changedOrders);
    }

    /**
     * The orders whose goods need a hand-over (FR-334).
     *
     * @return an unmodifiable copy, in order
     */
    public List<OrderId> needsGoodsHandOver() {
        return List.copyOf(needsGoodsHandOver);
    }

    /**
     * When it was created, which a decline never precedes (ADR-112).
     *
     * @return the creation's time
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Whether the proposal withdraws a vehicle.
     *
     * @param vehicle the vehicle
     * @return {@code true} when it is among the withdrawn
     */
    public boolean withdraws(VehicleId vehicle) {
        return withdrawn.stream().anyMatch(withdrawal -> withdrawal.vehicle().equals(vehicle));
    }

    /** Sets the kind and who declined it and when. */
    private void storeState(State next) {
        switch (next) {
            case State.Ready ready -> {
                state = StateKind.READY;
                declinedBy = null;
                declinedAt = null;
            }
            case State.Declined declined -> {
                state = StateKind.DECLINED;
                declinedBy = declined.by();
                declinedAt = declined.at();
            }
        }
    }

    /**
     * Checks the state a command left against the invariants at rest (ADR-126 decision 11). Every
     * input was checked before this point, so a broken invariant here is a bug in the command, not a
     * refusal, and the error rolls its transaction back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a proposal command left a state that breaks: " + broken);
        });
    }

    /**
     * The one check of the stored fields, shared by the load check and every command. Rebuilding
     * the driver changes checks their stored orders on the way.
     */
    private Optional<String> brokenRule() {
        driverChanges();
        Optional<PlanningError> broken = SolutionShape.brokenBy(routes(), dropped(), withdrawn);
        if (broken.isPresent()) {
            return Optional.of(broken.get() instanceof OrderAlreadyInPlan ? ORDER_ONCE : ONE_ROUTE_EACH);
        }
        if (state() instanceof State.Declined declined && declined.at().isBefore(createdAt)) {
            return Optional.of(DECLINED_AFTER_CREATED);
        }
        return Optional.empty();
    }
}
