package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.basis.ChangesSinceBasis;
import com.dispatchflow.planning.domain.basis.VehicleStanding;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.reconciliation.LateFact;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.planning.domain.solution.ObjectiveTerms;
import com.dispatchflow.planning.domain.solution.ReoptimizationResult;
import com.dispatchflow.planning.domain.solution.RoutePlan;
import com.dispatchflow.planning.domain.solution.RouteTimes;
import com.dispatchflow.planning.domain.solution.SolveFailure;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.SolvedStop;
import com.dispatchflow.planning.domain.solution.StopPlan;
import com.dispatchflow.planning.domain.solution.StopTimes;
import com.dispatchflow.planning.domain.solution.StopTiming;
import com.dispatchflow.planning.domain.solution.TravelLeg;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.SolveFailureReason;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AsSaved;
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
import com.dispatchflow.shared.StoredVersions;
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
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Test data for Planning: fixed IDs, times and the one builder per aggregate ({@link #aPlan()},
 * {@link #aJob()}). Every state is reached through the aggregates' own commands, then saved: a plan
 * is reloaded through {@code restore}, unless its builder is given other {@link Saves}, and a job is
 * saved in memory by {@link AsSaved#asSaved}, so each test starts with no pending events.
 */
public final class PlanningFixtures {

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

    public static final VehicleId V1 = VehicleId.of(uuid(101));
    public static final VehicleId V2 = VehicleId.of(uuid(102));
    public static final VehicleId V3 = VehicleId.of(uuid(103));
    public static final AccountId D1 = AccountId.of(uuid(201));
    public static final AccountId D2 = AccountId.of(uuid(202));
    static final AccountId D3 = AccountId.of(uuid(203));
    public static final OrderId O1 = OrderId.of(uuid(301));
    public static final OrderId O2 = OrderId.of(uuid(302));
    public static final OrderId O3 = OrderId.of(uuid(303));
    static final OrderId O4 = OrderId.of(uuid(304));
    static final OrderId O5 = OrderId.of(uuid(305));
    static final OrderId O6 = OrderId.of(uuid(306));
    static final Attempt FIRST = Attempt.FIRST;

    private static final long KEY_PREFIX = 0x0190a5e27c3b7defL;
    private static final long FRESH_BASE = 10_000;
    private static final int FRESH_COUNT = 215;

    /** Keeps a plan between two of its builder's commands as a save and a reload through {@code restore}. */
    private static final Saves IN_MEMORY = new Saves() {
        @Override
        public Plan added(Plan opened) {
            return reload(opened);
        }

        @Override
        public Plan changed(Plan plan, Consumer<Plan> command) {
            command.accept(plan);
            return reload(plan);
        }
    };

    private PlanningFixtures() {}

    public static UUID uuid(long n) {
        return new UUID(KEY_PREFIX, n);
    }

    static FreshIds freshIds(long from) {
        return freshIds(KEY_PREFIX, from);
    }

    private static FreshIds freshIds(long prefix, long from) {
        return FreshIds.of(IntStream.range(0, FRESH_COUNT)
                .mapToObj(i -> new UUID(prefix, FRESH_BASE + from + i))
                .toList());
    }

    /** A solve's time on {@link #DATE}, which may pass its end. */
    public static SolveTime at(long seconds) {
        return SolveTime.of(DATE, seconds);
    }

    /** A time that must lie in {@link #DATE}, such as a drop fact's limit. */
    public static DaySeconds dayAt(long seconds) {
        return DaySeconds.of(DATE, seconds);
    }

    static StopPlan stopPlan(long arrival) {
        return new StopPlan(
                StopTimes.of(at(arrival), at(arrival), at(arrival + 300)),
                TravelLeg.of(Duration.ofSeconds(600), 5_000));
    }

    public static Optional<RoutePlan> routePlan(long returnsAt) {
        return Optional.of(new RoutePlan(at(25_200), at(returnsAt), TravelLeg.of(Duration.ofSeconds(600), 5_000)));
    }

    /** A solved route; one with no stops is not driven, so the validator gives it no times. */
    public static SolvedRoute solved(VehicleId vehicle, AccountId driver, OrderId... orders) {
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

    public static Violation violation(String rule, RouteId route) {
        return Violation.onRoute(RuleCode.of(rule), route);
    }

    /** Saves and reloads a plan: the next version, no pending events, nothing pending. */
    static Plan reload(Plan plan) {
        PlanSnapshot s = plan.snapshot();
        return Plan.restore(new PlanSnapshot(
                s.id(),
                StoredVersions.afterSave(plan),
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

    static PlanSnapshot.RouteSnapshot routeOf(Plan plan, VehicleId vehicle) {
        return plan.snapshot().routes().stream()
                .filter(route ->
                        route.vehicle().equals(vehicle) && route.withdrawal().isEmpty())
                .findFirst()
                .orElseThrow();
    }

    public static RouteId routeId(Plan plan, VehicleId vehicle) {
        return routeOf(plan, vehicle).id();
    }

    public static StopId stopId(Plan plan, OrderId order) {
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

    public static PlanBuilder aPlan() {
        return new PlanBuilder();
    }

    static JobBuilder aJob() {
        return new JobBuilder();
    }

    public static EvaluationBuilder evaluationOf(Plan plan) {
        return new EvaluationBuilder(plan.version(), plan.layout());
    }

    /** An evaluation of the layout one of the plan's queries returned, as the use case asks for it. */
    static EvaluationBuilder evaluationOf(Plan plan, PlanLayout after) {
        return new EvaluationBuilder(plan.version(), after);
    }

    /**
     * How a builder keeps a plan between two of its commands: in memory, as a save and a reload
     * through {@code restore}, or through a repository, as a persistence integration test does.
     */
    public interface Saves {

        /**
         * Saves a plan never saved.
         *
         * @param opened the plan as {@code Plan.open} returned it
         * @return the plan as it loads again
         */
        Plan added(Plan opened);

        /**
         * Runs one command on a saved plan, and saves it.
         *
         * @param plan the plan as it last loaded
         * @param command the command, given the plan to change
         * @return the plan as it loads again
         */
        Plan changed(Plan plan, Consumer<Plan> command);
    }

    /**
     * Builds a plan in a named state, through its own commands. By default it keys the plan on
     * {@link #PLAN} and {@link #DATE} and keeps it in memory between commands.
     */
    public static final class PlanBuilder {

        private ValidatedSolution solution = standardSolution();
        private long keys = KEY_PREFIX;
        private DeliveryDate date = DATE;
        private Saves saves = IN_MEMORY;

        PlanBuilder solvedAs(ValidatedSolution newSolution) {
            this.solution = newSolution;
            return this;
        }

        /**
         * Keys the plan, its new routes and stops and its late stop on a prefix of its own, and opens
         * it for a date of its own, as tests that share one database need.
         *
         * @param keyPrefix the most significant bits of every UUID the plan's commands are given
         * @param onDate the plan's date, which is also the date its commands take as today
         * @return this builder
         */
        public PlanBuilder keyed(long keyPrefix, DeliveryDate onDate) {
            this.keys = keyPrefix;
            this.date = onDate;
            return this;
        }

        /**
         * Keeps the plan between commands through the given saves.
         *
         * @param through how the plan is saved and loaded again
         * @return this builder
         */
        public PlanBuilder savedBy(Saves through) {
            this.saves = through;
            return this;
        }

        public Plan draft() {
            return saves.added(Plan.open(PlanId.of(key(1)), date, PlansOnDate.none(), today(), NOW));
        }

        public Plan solving() {
            return saves.changed(
                    draft(),
                    plan -> plan.requestSolve(
                            JOB,
                            SolverName.OR_TOOLS,
                            SolveLimit.DEFAULT_DAILY,
                            Seed.of(7),
                            roomIn(Lane.DAILY),
                            today(),
                            NOW));
        }

        public Plan ready() {
            return saves.changed(
                    solving(),
                    plan -> plan.applySolveResult(succeededJob(plan.id(), JOB, solution), freshIds(keys, 0), NOW));
        }

        /** Ready, then solving again: a failed solve returns it to ready. */
        public Plan solvingAgain() {
            return saves.changed(
                    ready(),
                    plan -> plan.requestSolve(
                            SECOND_JOB,
                            SolverName.OR_TOOLS,
                            SolveLimit.DEFAULT_DAILY,
                            Seed.of(8),
                            roomIn(Lane.DAILY),
                            today(),
                            LATER));
        }

        /**
         * Ready with a pin, a lock, an open violation, three edits to undo and one to redo: O5 is
         * pinned to V1, O1 moved to the front of V2 with BR-03 there, V1 locked, and O4's pin undone.
         */
        public Plan readyEdited() {
            Plan ready = ready();
            RouteId v1 = routeId(ready, V1);
            RouteId v2 = routeId(ready, V2);
            StopId o1 = stopId(ready, O1);
            Violation late = violation("BR-03", v2);
            Plan pinned = saves.changed(
                    ready,
                    plan -> plan.pin(
                            O5, new PinTarget.ToVehicle(V1), evaluationOf(plan).build(), today()));
            Plan moved = saves.changed(
                    pinned,
                    plan -> plan.moveStop(
                            o1,
                            v2,
                            0,
                            evaluationOf(plan)
                                    .moving(o1, v2, 0)
                                    .withViolation(late)
                                    .build(),
                            today()));
            Plan locked = saves.changed(moved, plan -> plan.lockRoute(v1, RouteLock.WHOLE_ROUTE, today()));
            Plan pinnedTwice = saves.changed(
                    locked,
                    plan -> plan.pin(
                            O4,
                            new PinTarget.ToVehicle(V2),
                            evaluationOf(plan).withViolation(late).build(),
                            today()));
            return saves.changed(
                    pinnedTwice,
                    plan -> plan.undo(evaluationOf(plan).withViolation(late).build(), today()));
        }

        public Plan published() {
            return saves.changed(ready(), plan -> plan.publish(checksFor(plan), DISPATCHER, today(), LATER));
        }

        /**
         * Published at revision 4: V1 withdrawn after O1, kept on its route (revision 2); O4 served
         * late on V2 from the drops (revision 3); V2 handed to D3 with a confirmed broken shift
         * (revision 4).
         */
        public Plan publishedWithFacts() {
            Plan withdrawn = saves.changed(published(), plan -> {
                SolveJob job = plan.requestReoptimization(
                        JobId.of(key(60)),
                        SolverName.OR_TOOLS,
                        Seed.of(1),
                        roomIn(Lane.REOPTIMIZATION),
                        today(),
                        LATER);
                job.claim(LATER);
                job.searchStarted(LATER);
                ValidatedSolution reoptimized = ValidatedSolution.of(ValidationReport.of(
                        List.of(),
                        List.of(solved(V1, D1), solved(V2, D2, O3, O2)),
                        List.of(dropped(O4, new DropReason.Displaced()), dropped(O5, new DropReason.MissingLocation())),
                        List.of()));
                ReoptimizationProposal proposal = job.succeed(
                        ProposalId.of(key(61)),
                        reoptimizationResult(
                                reoptimized, new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE)),
                        LATER);
                plan.accept(proposal, noChanges(V2), freshIds(keys, 700), DISPATCHER, today(), LATER);
            });
            RouteId v2 = routeId(withdrawn, V2);
            Plan late = saves.changed(
                    withdrawn,
                    plan -> plan.reconcileLateOutcome(new LateFact(StopId.of(key(62)), O4, FIRST, v2), LATER));
            return saves.changed(late, plan -> {
                Evaluation breaks = evaluationOf(plan)
                        .handingOver(v2, D3)
                        .withViolation(violation("BR-06", v2))
                        .returningAt(70_000)
                        .build();
                plan.handOver(
                        v2, new DriverStanding(D3, true, true), Optional.of(breaks), true, DISPATCHER, today(), LATER);
            });
        }

        public Plan completed() {
            return saves.changed(published(), plan -> plan.complete(tomorrow()));
        }

        public Plan cancelled() {
            return saves.changed(published(), plan -> plan.cancel(StartedRoutes.none(), today(), LATER));
        }

        public Plan abandoned() {
            return saves.changed(ready(), plan -> plan.abandon(tomorrow()));
        }

        /**
         * The date the plan's commands take as today: its own date, so none of them is refused as
         * past.
         *
         * @return the date
         */
        public LocalDate today() {
            return date.value();
        }

        private LocalDate tomorrow() {
            return today().plusDays(1);
        }

        private UUID key(long n) {
            return new UUID(keys, n);
        }
    }

    /** A daily job of {@link #PLAN} that succeeded with a solution. */
    static SolveJob succeededJob(JobId id, ValidatedSolution solution) {
        return succeededJob(PLAN, id, solution);
    }

    /** A daily job of a plan that succeeded with a solution. */
    private static SolveJob succeededJob(PlanId plan, JobId id, ValidatedSolution solution) {
        SolveJob job = SolveJob.queue(
                id,
                plan,
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
        return asSaved(job);
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
            return asSaved(
                    SolveJob.queue(JOB, PLAN, lane, SolverName.OR_TOOLS, limit, Seed.of(7), basis, roomIn(lane), NOW));
        }

        SolveJob buildingMatrix() {
            SolveJob job = queued();
            job.claim(NOW.plusSeconds(10));
            return asSaved(job);
        }

        SolveJob running() {
            SolveJob job = buildingMatrix();
            job.searchStarted(NOW.plusSeconds(20));
            return asSaved(job);
        }

        SolveJob succeeded() {
            SolveJob job = running();
            if (lane == Lane.DAILY) {
                job.succeed(standardSolution(), NOW.plusSeconds(60));
            } else {
                job.succeed(PROPOSAL, reoptimizationResult(standardSolution()), NOW.plusSeconds(60));
            }
            return asSaved(job);
        }

        SolveJob failed() {
            SolveJob job = running();
            job.fail(SolveFailure.of(SolveFailureReason.OPTIMIZER_UNAVAILABLE), NOW.plusSeconds(60));
            return asSaved(job);
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
    public static final class EvaluationBuilder {

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
        public EvaluationBuilder handingOver(RouteId route, AccountId driver) {
            drivers.put(route, driver);
            return this;
        }

        public EvaluationBuilder moving(StopId stop, RouteId to, int position) {
            removing(stop);
            routes.get(to).add(position, stop);
            return this;
        }

        public EvaluationBuilder removing(StopId stop) {
            routes.values().forEach(stops -> stops.remove(stop));
            return this;
        }

        public EvaluationBuilder inserting(RouteId route, int position, StopId stop) {
            routes.get(route).add(position, stop);
            return this;
        }

        public EvaluationBuilder withViolation(Violation violation) {
            violations.add(violation);
            return this;
        }

        public EvaluationBuilder returningAt(long seconds) {
            this.returnsAt = seconds;
            return this;
        }

        /** The stop as the validator gives one it can't reach (BR-14): no times. */
        public EvaluationBuilder untimed(StopId stop) {
            untimed.add(stop);
            return this;
        }

        public Evaluation build() {
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

        public Evaluation unavailable() {
            return Evaluation.unavailable(version);
        }
    }
}
