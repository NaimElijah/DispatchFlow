package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.DriverRoutedTwice;
import com.dispatchflow.planning.domain.PlanningError.OrderAlreadyInPlan;
import com.dispatchflow.planning.domain.PlanningError.ProposalAlreadyDeclined;
import com.dispatchflow.planning.domain.PlanningError.VehicleRoutedTwice;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A re-optimization's result, waiting for a dispatcher (DL §4.4, §17 "Proposal"). It never changes,
 * except to be declined once; nothing reaches drivers until a dispatcher accepts it, and accepting
 * it is a revision of the plan that cites it (FR-255, BR-26, ADR-056). Its basis is what acceptance
 * judges the changes since against (BR-25, BR-82).
 */
public final class ReoptimizationProposal extends AggregateRoot {

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

    private final ReoptimizationProposalSnapshot content;
    private State state;

    private ReoptimizationProposal(ReoptimizationProposalSnapshot content) {
        super(content.version());
        this.content = content;
        this.state = content.state();
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
     * @throws RuleViolation with what {@code restore} would refuse, so no stored proposal fails to
     *     load: {@link OrderAlreadyInPlan} for an order and attempt served or dropped twice (BR-07),
     *     {@link VehicleRoutedTwice} for a vehicle with two routes, withdrawn or not, or {@link
     *     DriverRoutedTwice} for a driver on two routes it doesn't withdraw (BR-39, ADR-101)
     */
    static ReoptimizationProposal create(
            ProposalId id, PlanId plan, JobId job, ProposalBasis basis, ReoptimizationResult result, Instant now) {
        Optional<PlanningError> broken =
                brokenBy(result.solution().routes(), result.solution().dropped(), result.withdrawn());
        if (broken.isPresent()) {
            throw new RuleViolation(broken.get());
        }
        return new ReoptimizationProposal(new ReoptimizationProposalSnapshot(
                id,
                Version.NEW,
                plan,
                job,
                basis,
                result.solution().routes(),
                result.solution().dropped(),
                result.withdrawn(),
                result.objective(),
                result.lateStops(),
                result.withoutUsablePosition(),
                result.driverChanges(),
                result.needsGoodsHandOver(),
                now,
                new State.Ready()));
    }

    /**
     * Rebuilds a stored proposal; only the persistence adapter calls it.
     *
     * @param snapshot the stored state
     * @return the proposal
     * @throws StoredStateInvalid when an order and attempt appears twice among its routes and drops
     *     (BR-07), a vehicle has two routes, a driver drives two routes it doesn't withdraw (BR-39), or it was
     *     declined before it was created
     */
    public static ReoptimizationProposal restore(ReoptimizationProposalSnapshot snapshot) {
        Optional<PlanningError> broken = brokenBy(snapshot.routes(), snapshot.dropped(), snapshot.withdrawn());
        if (broken.isPresent()) {
            throw new StoredStateInvalid(
                    ReoptimizationProposal.class,
                    broken.get() instanceof OrderAlreadyInPlan
                            ? "an order and attempt is served or dropped at most once"
                            : "a vehicle or driver drives at most one route");
        }
        if (snapshot.state() instanceof State.Declined declined && declined.at().isBefore(snapshot.createdAt())) {
            throw new StoredStateInvalid(
                    ReoptimizationProposal.class, "a proposal is declined no earlier than it is created");
        }
        return new ReoptimizationProposal(snapshot);
    }

    /**
     * A dispatcher declines the proposal; nothing else changes (BR-26, DL §6.2). An accepted proposal
     * may still be declined: acceptance lives on the plan, so this changes nothing it applied
     * (ADR-101).
     *
     * @param by who declines it
     * @param now when
     * @throws RuleViolation with {@link ProposalAlreadyDeclined} when it was declined before
     */
    public void decline(AccountId by, Instant now) {
        if (state instanceof State.Declined) {
            throw new RuleViolation(new ProposalAlreadyDeclined(content.id()));
        }
        state = new State.Declined(by, now);
    }

    /**
     * The proposal's ID.
     *
     * @return the ID
     */
    public ProposalId id() {
        return content.id();
    }

    /**
     * The proposal's state, for the repository, for {@link Plan#accept}, and for tests.
     *
     * @return a snapshot that {@link #restore} rebuilds
     */
    public ReoptimizationProposalSnapshot snapshot() {
        return new ReoptimizationProposalSnapshot(
                content.id(),
                version(),
                content.plan(),
                content.job(),
                content.basis(),
                content.routes(),
                content.dropped(),
                content.withdrawn(),
                content.objective(),
                content.lateStops(),
                content.withoutUsablePosition(),
                content.driverChanges(),
                content.needsGoodsHandOver(),
                content.createdAt(),
                state);
    }

    /**
     * The first rule a proposal's routes and drops break, which both {@link #create} and {@link
     * #restore} check: an order and attempt once (BR-07), a vehicle on one route, and a driver on one
     * route it isn't withdrawn from (BR-39).
     */
    private static Optional<PlanningError> brokenBy(
            List<SolvedRoute> routes, List<DroppedOrder> dropped, List<VehicleWithdrawal> withdrawn) {
        Set<StopKey> seen = new HashSet<>();
        List<StopKey> keys = new ArrayList<>();
        routes.forEach(route -> route.stops().forEach(stop -> keys.add(new StopKey(stop.order(), stop.attempt()))));
        dropped.forEach(drop -> keys.add(new StopKey(drop.order(), drop.attempt())));
        for (StopKey key : keys) {
            if (!seen.add(key)) {
                return Optional.of(new OrderAlreadyInPlan(key.order(), key.attempt()));
            }
        }
        Set<VehicleId> withdrawnVehicles = new HashSet<>();
        withdrawn.forEach(withdrawal -> withdrawnVehicles.add(withdrawal.vehicle()));
        Set<VehicleId> vehicles = new HashSet<>();
        Set<AccountId> drivers = new HashSet<>();
        for (SolvedRoute route : routes) {
            if (!vehicles.add(route.vehicle())) {
                return Optional.of(new VehicleRoutedTwice(route.vehicle()));
            }
            if (!withdrawnVehicles.contains(route.vehicle()) && !drivers.add(route.driver())) {
                return Optional.of(new DriverRoutedTwice(route.driver()));
            }
        }
        return Optional.empty();
    }
}
