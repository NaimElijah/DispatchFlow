package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.drop.ExcludedVehicle;
import com.dispatchflow.planning.domain.drop.ExclusionCause;
import com.dispatchflow.planning.domain.solution.DriverChange;
import com.dispatchflow.planning.domain.solution.LateStop;
import com.dispatchflow.planning.domain.solution.ObjectiveTerms;
import com.dispatchflow.planning.domain.solution.ReoptimizationResult;
import com.dispatchflow.planning.domain.solution.RoutePlan;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.SolvedStop;
import com.dispatchflow.planning.domain.solution.StopPlan;
import com.dispatchflow.planning.domain.solution.StopTimes;
import com.dispatchflow.planning.domain.solution.TravelLeg;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The solutions, violations and preview facts the persistence tests of solve jobs and proposals
 * store: values with every shape their tables can take. None of them is a key, so the tests share
 * them; each test keys its own jobs, plans and proposals.
 */
final class StoredSolutions {

    private static final long VALUE_PREFIX = 0x0190a5e2_7661_6c75L;

    static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 8, 10));
    static final VehicleId V1 = VehicleId.of(uuid(1));
    static final VehicleId V2 = VehicleId.of(uuid(2));
    static final VehicleId V3 = VehicleId.of(uuid(3));
    static final AccountId D1 = AccountId.of(uuid(11));
    static final AccountId D2 = AccountId.of(uuid(12));
    static final AccountId D3 = AccountId.of(uuid(13));
    static final RouteId ROUTE = RouteId.of(uuid(21));

    private StoredSolutions() {}

    /**
     * A solution with every shape a stored route, stop, drop and exclusion can take: V1 serves orders
     * 1 and 2 (a second attempt), V2 serves none and has no plan, V3 serves order 3; times hold a
     * fraction of a second and pass the day's end; and every drop reason is there, both kinds of pin
     * included, on orders 10 to 21.
     *
     * @return the solution
     */
    static ValidatedSolution everyKindOfSolution() {
        TravelLeg leg = TravelLeg.of(Duration.ofSeconds(600).plusNanos(1), 5_000);
        RoutePlan plan = new RoutePlan(at(25_200), at(90_000), leg);
        List<SolvedRoute> routes = List.of(
                new SolvedRoute(
                        V1,
                        D1,
                        Optional.of(plan),
                        List.of(stop(1, Attempt.FIRST, 28_800), stop(2, Attempt.of(2), 30_000))),
                new SolvedRoute(V2, D2, Optional.empty(), List.of()),
                new SolvedRoute(V3, D3, Optional.of(plan), List.of(stop(3, Attempt.FIRST, 32_000))));
        List<DropReason> reasons = List.of(
                new DropReason.MissingLocation(),
                new DropReason.NoVehicleAvailable(),
                new DropReason.UnreachableLocation(),
                new DropReason.RemovedByDispatcher(),
                new DropReason.PinNotHonoured(new PinTarget.ToVehicle(V1), true),
                new DropReason.PinNotHonoured(new PinTarget.ToDriver(D2), false),
                new DropReason.Capacity(
                        Weight.kilograms(new BigDecimal("120.5")),
                        Volume.litres(new BigDecimal("800")),
                        Weight.kilograms(new BigDecimal("100")),
                        Volume.litres(new BigDecimal("600.25"))),
                new DropReason.TimeWindow(DaySeconds.of(DATE, 43_200), at(90_001)),
                new DropReason.Shift(DaySeconds.of(DATE, 64_800), at(70_000)),
                new DropReason.DepotHours(DaySeconds.of(DATE, 72_000), at(80_000)),
                new DropReason.VehicleLimit(),
                new DropReason.Displaced());
        List<DroppedOrder> drops = new ArrayList<>();
        for (int i = 0; i < reasons.size(); i++) {
            drops.add(new DroppedOrder(order(10 + i), Attempt.FIRST, reasons.get(i)));
        }
        List<ExcludedVehicle> excluded = List.of(
                new ExcludedVehicle(VehicleId.of(uuid(4)), ExclusionCause.DEPOT_WITHOUT_LOCATION),
                new ExcludedVehicle(VehicleId.of(uuid(5)), ExclusionCause.WITHDRAWN));
        return ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, excluded));
    }

    /**
     * A solution with no route, drop or excluded vehicle.
     *
     * @return the solution
     */
    static ValidatedSolution emptySolution() {
        return ValidatedSolution.of(ValidationReport.of(List.of(), List.of(), List.of(), List.of()));
    }

    /**
     * One violation with every part, and one that names only its route.
     *
     * @return the two violations
     */
    static List<Violation> violations() {
        return List.of(
                new Violation(
                        RuleCode.of("BR-05"),
                        Optional.of(ROUTE),
                        Optional.of(order(1)),
                        Optional.of(V1),
                        Optional.of(D1),
                        Optional.of(Duration.ofSeconds(90).plusNanos(5)),
                        Optional.of(new BigDecimal("12.5")),
                        Optional.of(new BigDecimal("3.25"))),
                Violation.onRoute(RuleCode.LOCKS, ROUTE));
    }

    /**
     * A re-optimization of a solution with every preview list filled: it withdraws V3, keeps order 1
     * late, finds V2 without a usable position, changes two drivers' orders, one of them in none of
     * the three lists, and hands over order 3's goods.
     *
     * @param solution the solution
     * @param factsAt when its facts were gathered
     * @return the re-optimization
     */
    static ReoptimizationResult everyKindOfReoptimization(ValidatedSolution solution, Instant factsAt) {
        return new ReoptimizationResult(
                solution,
                factsAt,
                List.of(new VehicleWithdrawal(V3, WithdrawalCause.SHIFT_RAN_OUT)),
                new ObjectiveTerms(
                        Duration.ofSeconds(3_600),
                        Duration.ofSeconds(120),
                        Duration.ofSeconds(30),
                        Duration.ZERO,
                        Duration.ofMillis(1_500),
                        Duration.ofSeconds(240),
                        Duration.ofNanos(7)),
                List.of(new LateStop(order(1), Attempt.FIRST, Duration.ofMinutes(5))),
                List.of(V2),
                List.of(
                        new DriverChange(D1, List.of(order(1), order(4)), List.of(order(5)), List.of(order(2))),
                        new DriverChange(D2, List.of(), List.of(), List.of())),
                List.of(order(3), order(1)));
    }

    /**
     * A re-optimization of a solution with every preview list empty.
     *
     * @param solution the solution
     * @param factsAt when its facts were gathered
     * @return the re-optimization
     */
    static ReoptimizationResult bareReoptimization(ValidatedSolution solution, Instant factsAt) {
        Duration none = Duration.ZERO;
        return new ReoptimizationResult(
                solution,
                factsAt,
                List.of(),
                new ObjectiveTerms(none, none, none, none, none, none, none),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    /**
     * An order the solutions name.
     *
     * @param n the order's number
     * @return the order's ID
     */
    static OrderId order(int n) {
        return OrderId.of(uuid(100 + n));
    }

    private static SolvedStop stop(int order, Attempt attempt, long arrival) {
        return new SolvedStop(
                order(order),
                attempt,
                new StopPlan(
                        StopTimes.of(at(arrival), at(arrival + 60), at(arrival + 360)),
                        TravelLeg.of(Duration.ofSeconds(420), 3_200)));
    }

    private static SolveTime at(long seconds) {
        return SolveTime.of(DATE, seconds);
    }

    private static UUID uuid(long n) {
        return new UUID(VALUE_PREFIX, n);
    }
}
