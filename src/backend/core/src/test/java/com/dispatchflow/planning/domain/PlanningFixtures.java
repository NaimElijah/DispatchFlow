package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.SolveFailureReason;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Test data for Planning: fixed IDs, times and the one builder per aggregate ({@link #aPlan()},
 * {@link #aJob()}). Every state is reached through the aggregates' own commands, then saved and
 * reloaded through {@code restore}, so each test starts with no pending events.
 */
final class PlanningFixtures {

    static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 6, 15));
    static final LocalDate TODAY = DATE.value();
    static final LocalDate TOMORROW = TODAY.plusDays(1);
    static final LocalDate YESTERDAY = TODAY.minusDays(1);
    static final Instant NOW = Instant.parse("2027-06-15T04:00:00Z");
    static final Instant LATER = NOW.plusSeconds(60);

    static final PlanId PLAN = PlanId.of(uuid(1));
    static final PlanId OTHER_PLAN = PlanId.of(uuid(2));
    static final JobId JOB = JobId.of(uuid(10));
    static final JobId SECOND_JOB = JobId.of(uuid(11));
    static final ProposalId PROPOSAL = ProposalId.of(uuid(20));
    static final AccountId DISPATCHER = AccountId.of(uuid(30));

    static final VehicleId V1 = VehicleId.of(uuid(101));
    static final VehicleId V2 = VehicleId.of(uuid(102));
    static final VehicleId V3 = VehicleId.of(uuid(103));
    static final AccountId D1 = AccountId.of(uuid(201));
    static final AccountId D2 = AccountId.of(uuid(202));
    static final AccountId D3 = AccountId.of(uuid(203));
    static final OrderId O1 = OrderId.of(uuid(301));
    static final OrderId O2 = OrderId.of(uuid(302));
    static final OrderId O3 = OrderId.of(uuid(303));
    static final OrderId O4 = OrderId.of(uuid(304));
    static final OrderId O5 = OrderId.of(uuid(305));
    static final OrderId O6 = OrderId.of(uuid(306));
    static final Attempt FIRST = Attempt.FIRST;

    private static final long FRESH_BASE = 10_000;
    private static final int FRESH_COUNT = 215;

    private PlanningFixtures() {}

    static UUID uuid(long n) {
        return new UUID(0x0190a5e27c3b7defL, n);
    }

    static FreshIds freshIds(long from) {
        return FreshIds.of(IntStream.range(0, FRESH_COUNT)
                .mapToObj(i -> uuid(FRESH_BASE + from + i))
                .toList());
    }

    /** A solve's time on {@link #DATE}, which may pass its end. */
    static SolveTime at(long seconds) {
        return SolveTime.of(DATE, seconds);
    }

    /** A time that must lie in {@link #DATE}, such as a drop fact's limit. */
    static DaySeconds dayAt(long seconds) {
        return DaySeconds.of(DATE, seconds);
    }

    static StopPlan stopPlan(long arrival) {
        return new StopPlan(
                StopTimes.of(at(arrival), at(arrival), at(arrival + 300)),
                TravelLeg.of(Duration.ofSeconds(600), 5_000));
    }

    static Optional<RoutePlan> routePlan(long returnsAt) {
        return Optional.of(new RoutePlan(at(25_200), at(returnsAt), TravelLeg.of(Duration.ofSeconds(600), 5_000)));
    }

    /** A solved route; one with no stops is not driven, so the validator gives it no times. */
    static SolvedRoute solved(VehicleId vehicle, AccountId driver, OrderId... orders) {
        List<SolvedStop> stops = new ArrayList<>();
        for (int i = 0; i < orders.length; i++) {
            stops.add(new SolvedStop(orders[i], FIRST, stopPlan(28_800 + 1_200L * i)));
        }
        return new SolvedRoute(vehicle, driver, orders.length == 0 ? Optional.empty() : routePlan(50_000), stops);
    }

    static DroppedOrder dropped(OrderId order, DropReason reason) {
        return new DroppedOrder(order, FIRST, reason);
    }

    /** The standard result: V1/D1 serves O1, O2; V2/D2 serves O3; O4 is displaced and O5 has no location. */
    static ValidatedSolution standardSolution() {
        return ValidatedSolution.of(ValidationReport.of(
                List.of(),
                List.of(solved(V1, D1, O1, O2), solved(V2, D2, O3)),
                List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                List.of()));
    }

    static LaneCapacity roomIn(Lane lane) {
        return LaneCapacity.readUnderLock(lane, List.of(), List.of());
    }

    static Violation violation(String rule, RouteId route) {
        return Violation.onRoute(RuleCode.of(rule), route);
    }

    /** Saves and reloads a plan: the next version, no pending events, nothing pending. */
    static Plan reload(Plan plan) {
        PlanSnapshot s = plan.snapshot();
        return Plan.restore(new PlanSnapshot(
                s.id(),
                plan.version().next(),
                s.date(),
                s.openedAt(),
                s.status(),
                s.applied(),
                s.routes(),
                s.dropped(),
                s.excluded(),
                s.pins(),
                s.violations(),
                s.undo(),
                s.redo()));
    }

    static SolveJob reload(SolveJob job) {
        SolveJobSnapshot s = job.snapshot();
        return SolveJob.restore(new SolveJobSnapshot(
                s.id(),
                job.version().next(),
                s.plan(),
                s.lane(),
                s.solver(),
                s.limit(),
                s.seed(),
                s.basisRevision(),
                s.requestedAt(),
                s.phase(),
                s.result()));
    }

    static PlanSnapshot.RouteSnapshot routeOf(Plan plan, VehicleId vehicle) {
        return plan.snapshot().routes().stream()
                .filter(route ->
                        route.vehicle().equals(vehicle) && route.withdrawal().isEmpty())
                .findFirst()
                .orElseThrow();
    }

    static RouteId routeId(Plan plan, VehicleId vehicle) {
        return routeOf(plan, vehicle).id();
    }

    static StopId stopId(Plan plan, OrderId order) {
        return plan.snapshot().routes().stream()
                .flatMap(route -> route.stops().stream())
                .filter(stop -> stop.order().equals(order))
                .map(PlanSnapshot.StopSnapshot::id)
                .findFirst()
                .orElseThrow();
    }

    static List<OrderId> ordersOn(Plan plan, VehicleId vehicle) {
        return routeOf(plan, vehicle).stops().stream()
                .map(PlanSnapshot.StopSnapshot::order)
                .toList();
    }

    static PlanBuilder aPlan() {
        return new PlanBuilder();
    }

    static JobBuilder aJob() {
        return new JobBuilder();
    }

    static EvaluationBuilder evaluationOf(Plan plan) {
        return new EvaluationBuilder(plan.version(), plan.layout());
    }

    /** An evaluation of the layout one of the plan's queries returned, as the use case asks for it. */
    static EvaluationBuilder evaluationOf(Plan plan, PlanLayout after) {
        return new EvaluationBuilder(plan.version(), after);
    }

    /** Builds a plan in a named state, through its own commands. */
    static final class PlanBuilder {

        private ValidatedSolution solution = standardSolution();

        PlanBuilder solvedAs(ValidatedSolution newSolution) {
            this.solution = newSolution;
            return this;
        }

        Plan draft() {
            return reload(Plan.open(PLAN, DATE, PlansOnDate.none(), TODAY, NOW));
        }

        Plan solving() {
            Plan plan = draft();
            plan.requestSolve(
                    JOB, SolverName.OR_TOOLS, SolveLimit.DEFAULT_DAILY, Seed.of(7), roomIn(Lane.DAILY), TODAY, NOW);
            return reload(plan);
        }

        Plan ready() {
            Plan plan = solving();
            plan.applySolveResult(succeededJob(JOB, solution), freshIds(0), NOW);
            return reload(plan);
        }

        /** Ready, then solving again: a failed solve returns it to ready. */
        Plan solvingAgain() {
            Plan plan = ready();
            plan.requestSolve(
                    SECOND_JOB,
                    SolverName.OR_TOOLS,
                    SolveLimit.DEFAULT_DAILY,
                    Seed.of(8),
                    roomIn(Lane.DAILY),
                    TODAY,
                    LATER);
            return reload(plan);
        }

        Plan published() {
            Plan plan = ready();
            plan.publish(checksFor(plan), DISPATCHER, TODAY, LATER);
            return reload(plan);
        }

        Plan completed() {
            Plan plan = published();
            plan.complete(TOMORROW);
            return reload(plan);
        }

        Plan cancelled() {
            Plan plan = published();
            plan.cancel(StartedRoutes.none(), TODAY, LATER);
            return reload(plan);
        }

        Plan abandoned() {
            Plan plan = ready();
            plan.abandon(TOMORROW);
            return reload(plan);
        }
    }

    /** A daily job of {@link #PLAN} that succeeded with a solution. */
    static SolveJob succeededJob(JobId id, ValidatedSolution solution) {
        SolveJob job = SolveJob.queue(
                id,
                PLAN,
                Lane.DAILY,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(7),
                Optional.empty(),
                roomIn(Lane.DAILY),
                NOW);
        job.claim(NOW);
        job.searchStarted(NOW);
        job.succeed(solution, NOW);
        return reload(job);
    }

    /**
     * A succeeded daily job of {@link #PLAN} rebuilt from storage with a result that {@code succeed}
     * refuses, so the plan's own backstop checks can be reached.
     */
    static SolveJob storedSucceededJob(JobId id, List<SolvedRoute> routes, List<DroppedOrder> dropped) {
        return SolveJob.restore(new SolveJobSnapshot(
                id,
                Version.NEW.next(),
                PLAN,
                Lane.DAILY,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(7),
                Optional.empty(),
                NOW,
                new SolveJob.Phase.Succeeded(NOW, NOW, NOW),
                Optional.of(new SolveResult(routes, dropped, List.of()))));
    }

    /** The checks under which a ready plan publishes: every route staffed, every order accounted for. */
    static PublishingChecks checksFor(Plan plan) {
        List<RouteStaffing> staffing = plan.snapshot().routes().stream()
                .map(route -> new RouteStaffing(route.vehicle(), route.driver(), true))
                .toList();
        Instant before = NOW.minusSeconds(3_600);
        List<DateOrder> orders = new ArrayList<>();
        plan.snapshot()
                .routes()
                .forEach(route -> route.stops()
                        .forEach(stop -> orders.add(new DateOrder(stop.order(), stop.attempt(), before, before))));
        plan.snapshot()
                .dropped()
                .forEach(drop -> orders.add(new DateOrder(drop.order(), drop.attempt(), before, before)));
        return new PublishingChecks(staffing, orders, evaluationOf(plan).build());
    }

    /** Builds a job in a named phase. */
    static final class JobBuilder {

        private Lane lane = Lane.DAILY;

        JobBuilder reoptimizing() {
            this.lane = Lane.REOPTIMIZATION;
            return this;
        }

        SolveJob queued() {
            SolveLimit limit = lane == Lane.DAILY ? SolveLimit.DEFAULT_DAILY : SolveLimit.REOPTIMIZATION;
            Optional<RevisionNo> basis = lane == Lane.DAILY ? Optional.empty() : Optional.of(RevisionNo.FIRST);
            return reload(
                    SolveJob.queue(JOB, PLAN, lane, SolverName.OR_TOOLS, limit, Seed.of(7), basis, roomIn(lane), NOW));
        }

        SolveJob buildingMatrix() {
            SolveJob job = queued();
            job.claim(NOW.plusSeconds(10));
            return reload(job);
        }

        SolveJob running() {
            SolveJob job = buildingMatrix();
            job.searchStarted(NOW.plusSeconds(20));
            return reload(job);
        }

        SolveJob succeeded() {
            SolveJob job = running();
            if (lane == Lane.DAILY) {
                job.succeed(standardSolution(), NOW.plusSeconds(60));
            } else {
                job.succeed(PROPOSAL, reoptimizationResult(standardSolution()), NOW.plusSeconds(60));
            }
            return reload(job);
        }

        SolveJob failed() {
            SolveJob job = running();
            job.fail(SolveFailure.of(SolveFailureReason.OPTIMIZER_UNAVAILABLE), NOW.plusSeconds(60));
            return reload(job);
        }
    }

    static ReoptimizationResult reoptimizationResult(ValidatedSolution solution, VehicleWithdrawal... withdrawn) {
        Duration none = Duration.ZERO;
        return new ReoptimizationResult(
                solution,
                LATER,
                List.of(withdrawn),
                new ObjectiveTerms(none, none, none, none, none, none, none),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    static ChangesSinceBasis noChanges(VehicleId... meetingBr20) {
        return new ChangesSinceBasis(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(meetingBr20).stream()
                        .map(vehicle -> new VehicleStanding(vehicle, true))
                        .toList());
    }

    /**
     * Builds an {@link Evaluation} that describes a plan as a change would leave it: starts from a
     * layout, the plan's or one its queries returned, and takes the same edit the plan will. Like the
     * validator, it gives a route with no stops no times (ADR-113).
     */
    static final class EvaluationBuilder {

        private final Version version;
        private final Map<RouteId, List<StopId>> routes = new LinkedHashMap<>();
        private final Map<RouteId, VehicleId> vehicles = new LinkedHashMap<>();
        private final Map<RouteId, AccountId> drivers = new LinkedHashMap<>();
        private final List<Violation> violations = new ArrayList<>();
        private final Set<StopId> untimed = new HashSet<>();
        private long returnsAt = 50_000;

        EvaluationBuilder(Version version, PlanLayout layout) {
            this.version = version;
            layout.routes().forEach(route -> {
                routes.put(
                        route.route(),
                        new ArrayList<>(route.stops().stream()
                                .map(PlanLayout.LaidOutStop::stop)
                                .toList()));
                vehicles.put(route.route(), route.vehicle());
                drivers.put(route.route(), route.driver());
            });
        }

        /** The route as a hand-over leaves it: judged with its new driver. */
        EvaluationBuilder handingOver(RouteId route, AccountId driver) {
            drivers.put(route, driver);
            return this;
        }

        EvaluationBuilder moving(StopId stop, RouteId to, int position) {
            removing(stop);
            routes.get(to).add(position, stop);
            return this;
        }

        EvaluationBuilder removing(StopId stop) {
            routes.values().forEach(stops -> stops.remove(stop));
            return this;
        }

        EvaluationBuilder inserting(RouteId route, int position, StopId stop) {
            routes.get(route).add(position, stop);
            return this;
        }

        EvaluationBuilder withViolation(Violation violation) {
            violations.add(violation);
            return this;
        }

        EvaluationBuilder returningAt(long seconds) {
            this.returnsAt = seconds;
            return this;
        }

        /** The stop as the validator gives one it can't reach (BR-14): no times. */
        EvaluationBuilder untimed(StopId stop) {
            untimed.add(stop);
            return this;
        }

        Evaluation build() {
            List<RouteTimes> times = new ArrayList<>();
            routes.forEach((route, stops) -> {
                List<StopTiming> timings = new ArrayList<>();
                for (int i = 0; i < stops.size(); i++) {
                    StopId stop = stops.get(i);
                    Optional<StopPlan> plan =
                            untimed.contains(stop) ? Optional.empty() : Optional.of(stopPlan(30_000 + 900L * i));
                    timings.add(new StopTiming(stop, plan));
                }
                Optional<RoutePlan> plan = stops.isEmpty() ? Optional.empty() : routePlan(returnsAt);
                times.add(new RouteTimes(route, vehicles.get(route), drivers.get(route), plan, timings));
            });
            return Evaluation.checked(version, violations, times);
        }

        Evaluation unavailable() {
            return Evaluation.unavailable(version);
        }
    }
}
