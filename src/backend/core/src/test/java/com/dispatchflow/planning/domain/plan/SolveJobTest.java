package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertJobRefused;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.JOB;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PLAN;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aJob;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reload;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.standardSolution;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.violation;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.PlanningError.DriverRoutedTwice;
import com.dispatchflow.planning.domain.PlanningError.JobAlreadyEnded;
import com.dispatchflow.planning.domain.PlanningError.JobNotBuildingMatrix;
import com.dispatchflow.planning.domain.PlanningError.JobNotQueued;
import com.dispatchflow.planning.domain.PlanningError.JobNotRunning;
import com.dispatchflow.planning.domain.PlanningError.OrderAlreadyInPlan;
import com.dispatchflow.planning.domain.PlanningError.ReoptimizationAlreadyRunning;
import com.dispatchflow.planning.domain.PlanningError.ResultForAnotherLane;
import com.dispatchflow.planning.domain.PlanningError.SolveQueueFull;
import com.dispatchflow.planning.domain.PlanningError.VehicleRoutedTwice;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.solution.SolveFailure;
import com.dispatchflow.planning.domain.solution.SolveResult;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.SolveFailed;
import com.dispatchflow.planning.events.SolveFailureReason;
import com.dispatchflow.planning.events.SolveSucceeded;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SolveJobTest {

    private static final Instant CLAIMED = NOW.plusSeconds(10);
    private static final Instant SEARCH = NOW.plusSeconds(20);
    private static final DropReason DISPLACED = new DropReason.Displaced();

    // ---- transitions ----------------------------------------------------------------------------

    @Test
    void GivenAQueuedJob_WhenClaimedAndItsSearchStarts_ThenItIsRunningFromTheSearchStart() {
        SolveJob job = aJob().queued();

        job.claim(CLAIMED);
        job.searchStarted(SEARCH);

        assertThat(job.snapshot().phase()).isEqualTo(new SolveJob.Phase.Running(CLAIMED, SEARCH));
        assertThat(job.pendingEvents()).isEmpty();
    }

    @Test
    void GivenARunningDailyJob_WhenItSucceeds_ThenItKeepsTheResultAndRaisesSolveSucceeded() {
        SolveJob job = aJob().running();
        Instant ended = NOW.plusSeconds(60);

        job.succeed(standardSolution(), ended);

        assertThat(job.snapshot().phase()).isEqualTo(new SolveJob.Phase.Succeeded(CLAIMED, SEARCH, ended));
        assertThat(job.result()).isPresent();
        assertThat(job.pendingEvents())
                .containsExactly(new SolveSucceeded(
                        new EventId("SolveSucceeded", JOB.value(), job.version().next(), 0),
                        ended,
                        JOB,
                        PLAN,
                        Lane.DAILY));
    }

    @Test
    void GivenARunningReoptimization_WhenItSucceeds_ThenItCreatesAReadyProposalOnItsBasis() {
        SolveJob job = aJob().reoptimizing().running();
        Instant ended = NOW.plusSeconds(60);

        ReoptimizationProposal proposal = job.succeed(PROPOSAL, reoptimizationResult(standardSolution()), ended);

        ReoptimizationProposalSnapshot snapshot = proposal.snapshot();
        assertThat(snapshot.plan()).isEqualTo(PLAN);
        assertThat(snapshot.job()).isEqualTo(JOB);
        assertThat(snapshot.basis().revision()).isEqualTo(RevisionNo.FIRST);
        assertThat(snapshot.state()).isEqualTo(new ReoptimizationProposal.State.Ready());
        assertThat(snapshot.routes()).isEqualTo(standardSolution().routes());
        assertThat(snapshot.dropped()).isEqualTo(standardSolution().dropped());
        assertThat(snapshot.createdAt()).isEqualTo(ended);
        assertThat(job.result()).isEmpty();
        assertThat(job.pendingEvents())
                .containsExactly(new SolveSucceeded(
                        new EventId("SolveSucceeded", JOB.value(), job.version().next(), 0),
                        ended,
                        JOB,
                        PLAN,
                        Lane.REOPTIMIZATION));
    }

    @Test
    void GivenARunningJob_WhenItFails_ThenItRecordsTheFailureAndRaisesSolveFailed() {
        SolveJob job = aJob().running();
        Instant ended = NOW.plusSeconds(60);
        SolveFailure rejected =
                SolveFailure.rejected(List.of(violation("BR-05", RouteId.of(PlanningFixtures.uuid(1_234)))));

        job.fail(rejected, ended);

        assertThat(job.snapshot().phase()).isEqualTo(new SolveJob.Phase.Failed(rejected, ended));
        assertThat(job.pendingEvents())
                .containsExactly(new SolveFailed(
                        new EventId("SolveFailed", JOB.value(), job.version().next(), 0),
                        ended,
                        JOB,
                        PLAN,
                        Lane.DAILY,
                        SolveFailureReason.VALIDATOR_REJECTED));
    }

    static Stream<Arguments> deadlines() {
        return Stream.of(
                Arguments.of(
                        "queued",
                        (Supplier<SolveJob>) () -> aJob().queued(),
                        NOW.plus(Duration.ofMinutes(5)),
                        SolveFailureReason.QUEUED_TOO_LONG),
                Arguments.of(
                        "building the matrix",
                        (Supplier<SolveJob>) () -> aJob().buildingMatrix(),
                        CLAIMED.plus(Duration.ofMinutes(2)),
                        SolveFailureReason.MATRIX_TOO_LONG),
                Arguments.of(
                        "running daily",
                        (Supplier<SolveJob>) () -> aJob().running(),
                        SEARCH.plusSeconds(45 + 30),
                        SolveFailureReason.OVERRAN),
                Arguments.of(
                        "running a re-optimization",
                        (Supplier<SolveJob>) () -> aJob().reoptimizing().running(),
                        SEARCH.plusSeconds(20 + 30),
                        SolveFailureReason.OVERRAN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deadlines")
    void GivenAJobAtItsDeadline_WhenTheWatchdogChecks_ThenNothingChanges(
            String phase, Supplier<SolveJob> job, Instant deadline, SolveFailureReason reason) {
        SolveJob atDeadline = job.get();
        SolveJobSnapshot before = atDeadline.snapshot();

        atDeadline.expireIfOverdue(deadline);

        assertThat(atDeadline.snapshot()).isEqualTo(before);
        assertThat(atDeadline.pendingEvents()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deadlines")
    void GivenAJobPastItsDeadline_WhenTheWatchdogChecks_ThenItFailsWithItsPhasesReason(
            String phase, Supplier<SolveJob> job, Instant deadline, SolveFailureReason reason) {
        SolveJob overdue = job.get();
        Instant past = deadline.plusSeconds(1);

        overdue.expireIfOverdue(past);

        assertThat(overdue.snapshot().phase()).isEqualTo(new SolveJob.Phase.Failed(SolveFailure.of(reason), past));
        assertThat(((SolveFailed) overdue.pendingEvents().get(0)).reason()).isEqualTo(reason);
    }

    // ---- a clock that steps back ----------------------------------------------------------------

    @Test
    void GivenAClockThatSteppedBack_WhenAQueuedJobIsClaimedBeforeItsRequest_ThenTheClaimCountsFromTheRequest() {
        SolveJob job = aJob().queued();

        job.claim(NOW.minusSeconds(1));

        assertThat(job.snapshot().phase()).isEqualTo(new SolveJob.Phase.BuildingMatrix(NOW));
        assertThat(reload(job).snapshot().phase()).isEqualTo(new SolveJob.Phase.BuildingMatrix(NOW));
    }

    @Test
    void GivenAClockThatSteppedBack_WhenTheSearchStartsBeforeTheClaim_ThenItCountsFromTheClaim() {
        SolveJob job = aJob().buildingMatrix();

        job.searchStarted(CLAIMED.minusSeconds(1));

        assertThat(reload(job).snapshot().phase()).isEqualTo(new SolveJob.Phase.Running(CLAIMED, CLAIMED));
    }

    @Test
    void GivenAClockThatSteppedBack_WhenADailyJobSucceedsBeforeItsSearchStarted_ThenItEndsWhenTheSearchStarted() {
        SolveJob job = aJob().running();

        job.succeed(standardSolution(), SEARCH.minusSeconds(1));

        assertThat(reload(job).snapshot().phase()).isEqualTo(new SolveJob.Phase.Succeeded(CLAIMED, SEARCH, SEARCH));
        assertThat(job.pendingEvents())
                .first()
                .extracting(event -> ((SolveSucceeded) event).occurredAt())
                .isEqualTo(SEARCH);
    }

    @Test
    void GivenAClockThatSteppedBack_WhenAReoptimizationSucceedsBeforeItsSearchStarted_ThenTheProposalIsCreatedThen() {
        SolveJob job = aJob().reoptimizing().running();

        ReoptimizationProposal proposal =
                job.succeed(PROPOSAL, reoptimizationResult(standardSolution()), SEARCH.minusSeconds(1));

        assertThat(reload(job).snapshot().phase()).isEqualTo(new SolveJob.Phase.Succeeded(CLAIMED, SEARCH, SEARCH));
        assertThat(proposal.snapshot().createdAt()).isEqualTo(SEARCH);
    }

    @Test
    void GivenAClockThatSteppedBack_WhenARunningJobFailsBeforeItsSearchStarted_ThenItEndsWhenTheSearchStarted() {
        SolveJob job = aJob().running();
        SolveFailure interrupted = SolveFailure.of(SolveFailureReason.INTERRUPTED);

        job.fail(interrupted, NOW.minusSeconds(1));

        assertThat(reload(job).snapshot().phase()).isEqualTo(new SolveJob.Phase.Failed(interrupted, SEARCH));
    }

    // ---- refusals -------------------------------------------------------------------------------

    @Test
    void GivenADailySolutionServingAnOrderTwice_WhenTheJobSucceeds_ThenOrderAlreadyInPlan() {
        SolveJob job = aJob().running();
        ValidatedSolution twice = ValidatedSolution.of(ValidationReport.of(
                List.of(),
                List.of(solved(V1, D1, O1), solved(V2, D2, O2)),
                List.of(dropped(O1, DISPLACED)),
                List.of()));

        assertJobRefused(job, () -> job.succeed(twice, SEARCH.plusSeconds(1)), new OrderAlreadyInPlan(O1, FIRST));
    }

    @Test
    void
            GivenADailySolutionServingAnOrderTwice_WhenTheWorkerFailsTheJobAsTheValidatorWould_ThenItIsValidatorRejected() {
        SolveJob job = aJob().running();
        ValidatedSolution twice = ValidatedSolution.of(
                ValidationReport.of(List.of(), List.of(solved(V1, D1, O1), solved(V2, D2, O1)), List.of(), List.of()));
        assertJobRefused(job, () -> job.succeed(twice, SEARCH.plusSeconds(1)), new OrderAlreadyInPlan(O1, FIRST));
        Violation servedTwice = new Violation(
                RuleCode.of("BR-07"),
                Optional.empty(),
                Optional.of(O1),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        job.fail(SolveFailure.rejected(List.of(servedTwice)), SEARCH.plusSeconds(1));

        assertThat(((SolveFailed) job.pendingEvents().get(0)).reason())
                .isEqualTo(SolveFailureReason.VALIDATOR_REJECTED);
    }

    @Test
    void GivenADailySolutionGivingAVehicleTwoRoutes_WhenTheJobSucceeds_ThenVehicleRoutedTwice() {
        SolveJob job = aJob().running();
        ValidatedSolution twice = ValidatedSolution.of(
                ValidationReport.of(List.of(), List.of(solved(V1, D1, O1), solved(V1, D2, O2)), List.of(), List.of()));

        assertJobRefused(job, () -> job.succeed(twice, SEARCH.plusSeconds(1)), new VehicleRoutedTwice(V1));
    }

    @Test
    void GivenADailySolutionPuttingADriverOnTwoRoutes_WhenTheJobSucceeds_ThenDriverRoutedTwice() {
        SolveJob job = aJob().running();
        ValidatedSolution twice = ValidatedSolution.of(
                ValidationReport.of(List.of(), List.of(solved(V1, D1, O1), solved(V2, D1, O2)), List.of(), List.of()));

        assertJobRefused(job, () -> job.succeed(twice, SEARCH.plusSeconds(1)), new DriverRoutedTwice(D1));
    }

    @Test
    void GivenAJobBuildingItsMatrix_WhenClaimedAgain_ThenJobNotQueued() {
        SolveJob job = aJob().buildingMatrix();

        assertJobRefused(job, () -> job.claim(SEARCH), new JobNotQueued(JOB));
    }

    @Test
    void GivenAQueuedJob_WhenItsSearchStarts_ThenJobNotBuildingMatrix() {
        SolveJob job = aJob().queued();

        assertJobRefused(job, () -> job.searchStarted(SEARCH), new JobNotBuildingMatrix(JOB));
    }

    @Test
    void GivenAJobBuildingItsMatrix_WhenItSucceeds_ThenJobNotRunning() {
        SolveJob job = aJob().buildingMatrix();

        assertJobRefused(job, () -> job.succeed(standardSolution(), SEARCH), new JobNotRunning(JOB));
    }

    @Test
    void GivenADailyJob_WhenItSucceedsWithAProposal_ThenResultForAnotherLane() {
        SolveJob job = aJob().running();

        assertJobRefused(
                job,
                () -> job.succeed(PROPOSAL, reoptimizationResult(standardSolution()), SEARCH),
                new ResultForAnotherLane(JOB, Lane.DAILY));
    }

    @Test
    void GivenAReoptimization_WhenItSucceedsWithADailyResult_ThenResultForAnotherLane() {
        SolveJob job = aJob().reoptimizing().running();

        assertJobRefused(
                job, () -> job.succeed(standardSolution(), SEARCH), new ResultForAnotherLane(JOB, Lane.REOPTIMIZATION));
    }

    @Test
    void GivenAFailedJob_WhenTheWatchdogLosesTheRaceAndExpiresIt_ThenJobAlreadyEnded() {
        SolveJob job = aJob().failed();

        assertJobRefused(job, () -> job.expireIfOverdue(NOW.plusSeconds(3_600)), new JobAlreadyEnded(JOB));
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    private static final List<String> PHASES = List.of("queued", "buildingMatrix", "running", "succeeded", "failed");

    private static SolveJob jobIn(String phase) {
        return switch (phase) {
            case "queued" -> aJob().queued();
            case "buildingMatrix" -> aJob().buildingMatrix();
            case "running" -> aJob().running();
            case "succeeded" -> aJob().succeeded();
            default -> aJob().failed();
        };
    }

    private static final List<Object[]> COMMANDS = List.of(
            new Object[] {
                "claim",
                (Consumer<SolveJob>) job -> job.claim(SEARCH),
                List.of(
                        SolveJob.Phase.BuildingMatrix.class,
                        JobNotQueued.class,
                        JobNotQueued.class,
                        JobAlreadyEnded.class,
                        JobAlreadyEnded.class)
            },
            new Object[] {
                "searchStarted",
                (Consumer<SolveJob>) job -> job.searchStarted(SEARCH),
                List.of(
                        JobNotBuildingMatrix.class,
                        SolveJob.Phase.Running.class,
                        JobNotBuildingMatrix.class,
                        JobAlreadyEnded.class,
                        JobAlreadyEnded.class)
            },
            new Object[] {
                "succeed",
                (Consumer<SolveJob>) job -> job.succeed(standardSolution(), SEARCH.plusSeconds(1)),
                List.of(
                        JobNotRunning.class,
                        JobNotRunning.class,
                        SolveJob.Phase.Succeeded.class,
                        JobAlreadyEnded.class,
                        JobAlreadyEnded.class)
            },
            new Object[] {
                "fail",
                (Consumer<SolveJob>)
                        job -> job.fail(SolveFailure.of(SolveFailureReason.INTERRUPTED), SEARCH.plusSeconds(1)),
                List.of(
                        SolveJob.Phase.Failed.class,
                        SolveJob.Phase.Failed.class,
                        SolveJob.Phase.Failed.class,
                        JobAlreadyEnded.class,
                        JobAlreadyEnded.class)
            },
            new Object[] {
                "expireIfOverdue",
                (Consumer<SolveJob>) job -> job.expireIfOverdue(NOW.plusSeconds(3_600)),
                List.of(
                        SolveJob.Phase.Failed.class,
                        SolveJob.Phase.Failed.class,
                        SolveJob.Phase.Failed.class,
                        JobAlreadyEnded.class,
                        JobAlreadyEnded.class)
            });

    static Stream<Arguments> lifecycle() {
        List<Arguments> cells = new ArrayList<>();
        for (Object[] command : COMMANDS) {
            List<?> row = (List<?>) command[2];
            for (int i = 0; i < PHASES.size(); i++) {
                cells.add(Arguments.of(PHASES.get(i), command[0], command[1], row.get(i)));
            }
        }
        return cells.stream();
    }

    @ParameterizedTest(name = "{0} × {1} → {3}")
    @MethodSource("lifecycle")
    void GivenEachPhaseOfAJob_WhenEachCommandRuns_ThenTheMatrixCellHolds(
            String phase, String command, Consumer<SolveJob> run, Class<?> expected) {
        SolveJob job = jobIn(phase);
        SolveJobSnapshot before = job.snapshot();

        if (PlanningError.class.isAssignableFrom(expected)) {
            assertThatThrownBy(() -> run.accept(job))
                    .isInstanceOfSatisfying(
                            RuleViolation.class,
                            violation -> assertThat(violation.error()).isInstanceOf(expected));
            assertThat(job.snapshot()).isEqualTo(before);
            assertThat(job.pendingEvents()).isEmpty();
        } else {
            run.accept(job);
            assertThat(job.snapshot().phase()).isInstanceOf(expected);
        }
    }

    static Stream<Arguments> reoptimizationSuccess() {
        return Stream.of(
                Arguments.of(
                        "queued",
                        (Supplier<SolveJob>) () -> aJob().reoptimizing().queued(),
                        new JobNotRunning(JOB)),
                Arguments.of(
                        "buildingMatrix",
                        (Supplier<SolveJob>) () -> aJob().reoptimizing().buildingMatrix(),
                        new JobNotRunning(JOB)),
                Arguments.of(
                        "succeeded",
                        (Supplier<SolveJob>) () -> aJob().reoptimizing().succeeded(),
                        new JobAlreadyEnded(JOB)),
                Arguments.of(
                        "failed",
                        (Supplier<SolveJob>) () -> aJob().reoptimizing().failed(),
                        new JobAlreadyEnded(JOB)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reoptimizationSuccess")
    void GivenAReoptimizationThatIsNotRunning_WhenItSucceedsWithAProposal_ThenJobNotRunningOrJobAlreadyEnded(
            String phase, Supplier<SolveJob> job, PlanningError expected) {
        SolveJob reoptimization = job.get();

        assertJobRefused(
                reoptimization,
                () -> reoptimization.succeed(PROPOSAL, reoptimizationResult(standardSolution()), SEARCH.plusSeconds(1)),
                expected);
    }

    @Test
    void GivenAFullReoptimizationLane_WhenAReoptimizationIsQueued_ThenSolveQueueFull() {
        LaneCapacity full = LaneCapacity.readUnderLock(Lane.REOPTIMIZATION, otherPlans(5), List.of());

        assertRefused(
                () -> SolveJob.queue(
                        JOB,
                        PLAN,
                        Lane.REOPTIMIZATION,
                        SolverName.OR_TOOLS,
                        SolveLimit.REOPTIMIZATION,
                        Seed.of(1),
                        Optional.of(RevisionNo.FIRST),
                        full,
                        NOW),
                new SolveQueueFull(Lane.REOPTIMIZATION, 5));
    }

    @Test
    void
            GivenAFullLaneThatAlsoHoldsThePlansReoptimization_WhenAnotherIsQueued_ThenReoptimizationAlreadyRunningComesFirst() {
        List<PlanId> queued = new ArrayList<>(otherPlans(4));
        queued.add(PLAN);
        LaneCapacity full = LaneCapacity.readUnderLock(Lane.REOPTIMIZATION, queued, List.of());

        assertRefused(
                () -> SolveJob.queue(
                        JOB,
                        PLAN,
                        Lane.REOPTIMIZATION,
                        SolverName.OR_TOOLS,
                        SolveLimit.REOPTIMIZATION,
                        Seed.of(1),
                        Optional.of(RevisionNo.FIRST),
                        full,
                        NOW),
                new ReoptimizationAlreadyRunning(PLAN));
    }

    @Test
    void GivenAFullReoptimizationLane_WhenThePlanRequestsAReoptimization_ThenSolveQueueFull() {
        Plan plan = PlanningFixtures.aPlan().published();
        LaneCapacity full = LaneCapacity.readUnderLock(Lane.REOPTIMIZATION, otherPlans(5), List.of());

        PlanAssertions.assertPlanRefused(
                plan,
                () -> plan.requestReoptimization(
                        JOB, SolverName.OR_TOOLS, Seed.of(1), full, PlanningFixtures.TODAY, NOW),
                new SolveQueueFull(Lane.REOPTIMIZATION, 5));
    }

    @Test
    void GivenFourQueuedJobsAndOneStarted_WhenAJobIsQueued_ThenItIsQueued() {
        LaneCapacity room = LaneCapacity.readUnderLock(Lane.DAILY, otherPlans(4), otherPlans(1));

        SolveJob job = SolveJob.queue(
                JOB,
                PLAN,
                Lane.DAILY,
                SolverName.OR_TOOLS,
                SolveLimit.DEFAULT_DAILY,
                Seed.of(1),
                Optional.empty(),
                room,
                NOW);

        assertThat(job.snapshot().phase()).isEqualTo(new SolveJob.Phase.Queued());
    }

    private static List<PlanId> otherPlans(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(n -> PlanId.of(PlanningFixtures.uuid(900 + n)))
                .toList();
    }

    // ---- restore --------------------------------------------------------------------------------

    static Stream<Arguments> builderPhases() {
        return Stream.<Supplier<SolveJob>>of(
                        () -> aJob().queued(),
                        () -> aJob().buildingMatrix(),
                        () -> aJob().running(),
                        () -> aJob().succeeded(),
                        () -> aJob().failed(),
                        () -> aJob().reoptimizing().queued(),
                        () -> aJob().reoptimizing().succeeded())
                .map(Supplier::get)
                .map(job -> Arguments.of(
                        job.snapshot().lane() + " "
                                + job.snapshot().phase().getClass().getSimpleName(),
                        job));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builderPhases")
    void GivenAJobInEachBuilderPhase_WhenItsSnapshotIsRestored_ThenTheSnapshotRoundTrips(String phase, SolveJob job) {
        assertThat(SolveJob.restore(job.snapshot()).snapshot()).isEqualTo(job.snapshot());
    }

    static Stream<Arguments> brokenJobs() {
        SolveJobSnapshot daily = aJob().succeeded().snapshot();
        SolveJobSnapshot reoptimization = aJob().reoptimizing().queued().snapshot();
        SolveJobSnapshot queued = aJob().queued().snapshot();
        return Stream.of(
                Arguments.of(
                        "a basis revision on a daily job",
                        "a basis revision belongs to a re-optimization",
                        withBasis(daily, Optional.of(RevisionNo.FIRST))),
                Arguments.of(
                        "a re-optimization without a basis revision",
                        "a basis revision belongs to a re-optimization",
                        withBasis(reoptimization, Optional.empty())),
                Arguments.of(
                        "a re-optimization limit other than 20 s",
                        "a re-optimization's limit is 20 seconds",
                        withLimit(reoptimization, SolveLimit.DEFAULT_DAILY)),
                Arguments.of(
                        "a succeeded daily job without its result",
                        "a result belongs to a succeeded daily job",
                        withResult(daily, Optional.empty())),
                Arguments.of(
                        "a queued job with a result",
                        "a result belongs to a succeeded daily job",
                        withResult(queued, daily.result())),
                Arguments.of(
                        "a claim before the request",
                        "the phase's times are out of order",
                        withPhase(queued, new SolveJob.Phase.BuildingMatrix(NOW.minusSeconds(1)))),
                Arguments.of(
                        "a search before the claim",
                        "the phase's times are out of order",
                        withPhase(queued, new SolveJob.Phase.Running(CLAIMED, NOW))),
                Arguments.of(
                        "an end before the search",
                        "the phase's times are out of order",
                        withPhase(
                                withResult(queued, daily.result()),
                                new SolveJob.Phase.Succeeded(CLAIMED, SEARCH, CLAIMED))),
                Arguments.of(
                        "a failure before the request",
                        "the phase's times are out of order",
                        withPhase(
                                queued,
                                new SolveJob.Phase.Failed(
                                        SolveFailure.of(SolveFailureReason.INTERRUPTED), NOW.minusSeconds(1)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenJobs")
    void GivenASnapshotThatBreaksAnAtRestInvariant_WhenAJobIsRestored_ThenStoredStateInvalid(
            String invariant, String detail, SolveJobSnapshot snapshot) {
        assertThatThrownBy(() -> SolveJob.restore(snapshot))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains(detail));
    }

    @Test
    void GivenAStoredFailureWhoseViolationsDontMatchItsReason_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> SolveFailure.ofStored(SolveFailureReason.VALIDATOR_REJECTED, List.of()))
                .isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> SolveFailure.ofStored(
                        SolveFailureReason.OVERRAN, List.of(violation("BR-05", RouteId.of(PlanningFixtures.uuid(1))))))
                .isInstanceOf(StoredStateInvalid.class);
    }

    @Test
    void GivenAStoredFailureWhoseViolationsMatchItsReason_WhenRebuilt_ThenItEqualsTheFactorysFailure() {
        List<Violation> violations = List.of(violation("BR-05", RouteId.of(PlanningFixtures.uuid(1))));

        assertThat(SolveFailure.ofStored(SolveFailureReason.VALIDATOR_REJECTED, violations))
                .isEqualTo(SolveFailure.rejected(violations));
        assertThat(SolveFailure.ofStored(SolveFailureReason.OVERRAN, List.of()))
                .isEqualTo(SolveFailure.of(SolveFailureReason.OVERRAN));
    }

    @Test
    void GivenAWorkerThatBuildsAFailureWhoseViolationsDontMatchItsReason_WhenBuilt_ThenItIsAProgrammingError() {
        assertThatThrownBy(() -> SolveFailure.rejected(List.of())).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> SolveFailure.of(SolveFailureReason.LOCK_NOT_HONOURED))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void GivenALockThatCouldNotBeKept_WhenTheJobFails_ThenTheFailureListsIt() {
        SolveJob job = aJob().running();
        SolveFailure lock =
                SolveFailure.lockNotHonoured(List.of(violation("FR-147", RouteId.of(PlanningFixtures.uuid(5)))));

        job.fail(lock, SEARCH.plusSeconds(1));

        assertThat(((SolveFailed) job.pendingEvents().get(0)).reason()).isEqualTo(SolveFailureReason.LOCK_NOT_HONOURED);
    }

    private static SolveJobSnapshot withBasis(SolveJobSnapshot s, Optional<RevisionNo> basis) {
        return new SolveJobSnapshot(
                s.id(),
                s.version(),
                s.plan(),
                s.lane(),
                s.solver(),
                s.limit(),
                s.seed(),
                basis,
                s.requestedAt(),
                s.phase(),
                s.result());
    }

    private static SolveJobSnapshot withLimit(SolveJobSnapshot s, SolveLimit limit) {
        return new SolveJobSnapshot(
                s.id(),
                s.version(),
                s.plan(),
                s.lane(),
                s.solver(),
                limit,
                s.seed(),
                s.basisRevision(),
                s.requestedAt(),
                s.phase(),
                s.result());
    }

    private static SolveJobSnapshot withResult(SolveJobSnapshot s, Optional<SolveResult> result) {
        return new SolveJobSnapshot(
                s.id(),
                s.version(),
                s.plan(),
                s.lane(),
                s.solver(),
                s.limit(),
                s.seed(),
                s.basisRevision(),
                s.requestedAt(),
                s.phase(),
                result);
    }

    private static SolveJobSnapshot withPhase(SolveJobSnapshot s, SolveJob.Phase phase) {
        return new SolveJobSnapshot(
                s.id(),
                s.version(),
                s.plan(),
                s.lane(),
                s.solver(),
                s.limit(),
                s.seed(),
                s.basisRevision(),
                s.requestedAt(),
                phase,
                s.result());
    }
}
