package com.dispatchflow.reporting.domain.baseline;

import com.dispatchflow.reporting.domain.CancelledOrders;
import com.dispatchflow.reporting.domain.PlannedOnTimeRate;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 */
public final class Baseline extends AggregateRoot {

    private final PlanId plan;
    private PlannedFigures result;
    private BaselineCause cause;
    private Instant computedAt;

    private Baseline(BaselineSnapshot stored) {
        super(stored.version());
        this.plan = stored.plan();
        this.result = stored.result();
        this.cause = stored.cause();
        this.computedAt = stored.computedAt();
    }

    /**
     * Computes a published plan's baseline (FR-39, FR-342). The listener on {@code PlanPublished}
     * calls it once per plan; a repeated delivery finds the baseline already stored.
     *
     * @param plan the published plan, which keys the baseline
     * @param result the nearest-neighbour solve's figures, on the plan's orders and fleet
     * @param now when it was computed
     * @return the baseline, caused by the publication, at {@link Version#NEW}
     */
    public static Baseline compute(PlanId plan, PlannedFigures result, Instant now) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(now, "now");
        return new Baseline(new BaselineSnapshot(plan, Version.NEW, result, new BaselineCause.Published(), now));
    }

    /**
     * Rebuilds a stored baseline, checking the invariant that holds at rest: a recomputed baseline
     * names at least one withdrawn vehicle or added order, and each one once. Only persistence
     * adapters call it (DL §3.5).
     *
     * @param stored the stored state
     * @return the baseline at the stored version
     * @throws StoredStateInvalid when the stored state breaks that invariant
     */
    public static Baseline restore(BaselineSnapshot stored) {
        Optional<String> broken = brokenBy(stored);
        if (broken.isPresent()) {
            throw new StoredStateInvalid(Baseline.class, broken.get());
        }
        return new Baseline(stored);
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
        BaselineCause.Recomputed before = recomputedSoFar();
        commit(
                newResult,
                new BaselineCause.Recomputed(
                        withdrawalsWith(before.withdrawn(), withdrawn), union(before.added(), added)),
                now);
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
        PlannedOnTimeRate planOnTime = planFigures.plannedOnTime(cancelled);
        PlannedOnTimeRate baselineOnTime = result.plannedOnTime(cancelled);
        List<VehicleId> withdrawn =
                recomputedSoFar().withdrawn().stream().map(Withdrawal::vehicle).toList();
        if (withdrawn.isEmpty()) {
            return new BaselineComparison.AllMeasures(
                    planFigures.distance(),
                    result.distance(),
                    planFigures.driverHours(),
                    result.driverHours(),
                    planOnTime,
                    baselineOnTime);
        }
        return new BaselineComparison.KilometresAndOnTime(
                planFigures.distance(), result.distance(), planOnTime, baselineOnTime, withdrawn);
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
        return WithdrawnFleet.of(withdrawalsWith(recomputedSoFar().withdrawn(), withdrawn));
    }

    /**
     * The plan this baseline is of.
     *
     * @return the plan's ID, which keys the baseline
     */
    public PlanId plan() {
        return plan;
    }

    /**
     * The baseline's state, for the repository to store and for tests.
     *
     * @return a snapshot that {@link #restore} rebuilds
     */
    public BaselineSnapshot snapshot() {
        return new BaselineSnapshot(plan, version(), result, cause, computedAt);
    }

    private BaselineCause.Recomputed recomputedSoFar() {
        return switch (cause) {
            case BaselineCause.Published published -> new BaselineCause.Recomputed(List.of(), List.of());
            case BaselineCause.Recomputed recomputed -> recomputed;
        };
    }

    /** The withdrawals named so far and the new ones, each vehicle once, with its first cause. */
    private static List<Withdrawal> withdrawalsWith(List<Withdrawal> named, List<Withdrawal> more) {
        List<Withdrawal> all = new ArrayList<>(named);
        for (Withdrawal withdrawal : more) {
            if (all.stream().noneMatch(each -> each.vehicle().equals(withdrawal.vehicle()))) {
                all.add(withdrawal);
            }
        }
        return all;
    }

    private static <T> List<T> union(List<T> named, List<T> more) {
        List<T> all = new ArrayList<>(named);
        more.stream().filter(item -> !all.contains(item)).forEach(all::add);
        return all;
    }

    /**
     * Replaces the state with the one a command built, after checking it holds the at-rest
     * invariant. Every input was checked before this point, so a broken invariant here is a bug in
     * the command, not a refusal.
     */
    private void commit(PlannedFigures nextResult, BaselineCause nextCause, Instant nextComputedAt) {
        Optional<String> broken =
                brokenBy(new BaselineSnapshot(plan, version(), nextResult, nextCause, nextComputedAt));
        if (broken.isPresent()) {
            throw new AssertionError("a baseline command would leave a state that breaks: " + broken.get());
        }
        result = nextResult;
        cause = nextCause;
        computedAt = nextComputedAt;
    }

    /** The invariant a baseline holds at rest, which {@link #restore} and {@link #commit} check. */
    private static Optional<String> brokenBy(BaselineSnapshot state) {
        if (state.cause() instanceof BaselineCause.Recomputed(List<Withdrawal> withdrawn, List<OrderId> added)) {
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
