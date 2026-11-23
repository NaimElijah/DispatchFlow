package com.dispatchflow.planning.adapter.out.persistence;

import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.everyKindOfReoptimization;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.everyKindOfSolution;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.violations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.domain.PlanningError.ReoptimizationAlreadyRunning;
import com.dispatchflow.planning.domain.PlanningError.SolveQueueFull;
import com.dispatchflow.planning.domain.plan.LaneCapacity;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.plan.PlansOnDate;
import com.dispatchflow.planning.domain.plan.QueuedJobs;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.domain.plan.SolveJobDescription;
import com.dispatchflow.planning.domain.solution.SolveFailure;
import com.dispatchflow.planning.domain.solution.SolveResult;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.planning.events.SolveFailureReason;
import com.dispatchflow.planning.events.SolveSucceeded;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainAssertions;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link SolveJobRepository} against a real PostgreSQL (ADR-126, ADR-127): what a job stores and loads
 * as in every phase, the version, the lane's lock and queue, the claim that skips a locked job, the
 * one running re-optimization per plan (BR-38), the unfinished jobs of a date's plans (BR-128), and
 * the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, so test {@code n} keys its own jobs and plans. The
 * queue's reads see every job of a lane, so the tests of their contents run in a transaction that
 * first ends every other unfinished job and is rolled back; the claim test, which needs two
 * transactions that commit, uses requests older than any other test's. The tests of a date's jobs
 * store their plans, through the plan's repository, on dates no other test uses.
 */
@IntegrationTest
class SolveJobPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_736f_6c76L;
    private static final Instant REQUESTED = Instant.parse("2027-08-10T05:00:00Z");
    private static final Instant CLAIMED = REQUESTED.plusSeconds(10);
    private static final Instant SEARCH = REQUESTED.plusSeconds(20);
    private static final Instant ENDED = REQUESTED.plusSeconds(60);
    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired
    private SolveJobRepository repository;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private Transactions transactions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedEvents events;

    @BeforeEach
    void forgetEarlierEvents() {
        events.clear();
    }

    // --- round trips (ADR-127 decision 1)

    /** The states a job stores and loads in, reached through queueing and the job's own commands. */
    enum Scenario {
        DAILY_QUEUED,
        DAILY_BUILDING_MATRIX,
        DAILY_RUNNING,
        DAILY_SUCCEEDED,
        DAILY_REJECTED,
        DAILY_FAILED_IN_THE_QUEUE,
        REOPTIMIZATION_QUEUED,
        REOPTIMIZATION_RUNNING,
        REOPTIMIZATION_SUCCEEDED,
        REOPTIMIZATION_LOCK_NOT_HONOURED;

        SolveJob build(JobId job, PlanId plan) {
            Lane lane = name().startsWith("DAILY") ? Lane.DAILY : Lane.REOPTIMIZATION;
            SolveJob built = QueuedJobs.queue(job, plan, lane, REQUESTED);
            switch (this) {
                case DAILY_QUEUED, REOPTIMIZATION_QUEUED -> {}
                case DAILY_FAILED_IN_THE_QUEUE ->
                    built.fail(SolveFailure.of(SolveFailureReason.QUEUED_TOO_LONG), ENDED);
                case DAILY_BUILDING_MATRIX -> built.claim(CLAIMED);
                default -> {
                    built.claim(CLAIMED);
                    built.searchStarted(SEARCH);
                    finish(built);
                }
            }
            return built;
        }

        private void finish(SolveJob running) {
            switch (this) {
                case DAILY_SUCCEEDED -> running.succeed(everyKindOfSolution(), ENDED);
                case DAILY_REJECTED -> running.fail(SolveFailure.rejected(violations()), ENDED);
                case REOPTIMIZATION_SUCCEEDED ->
                    running.succeed(
                            ProposalId.of(uuid(9_301)), everyKindOfReoptimization(everyKindOfSolution(), ENDED), ENDED);
                case REOPTIMIZATION_LOCK_NOT_HONOURED ->
                    running.fail(
                            SolveFailure.lockNotHonoured(List.of(violations().get(1))), ENDED);
                default -> {}
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenAJob_WhenAddedAndLoadedInAnotherTransaction_ThenItsWholeStateComesBackAsAdded(Scenario scenario) {
        JobId id = job(1 + scenario.ordinal());
        PlanId plan = plan(1 + scenario.ordinal());
        SolveJobDescription added = transactions.inTransaction(() -> {
            SolveJob job = scenario.build(id, plan);
            SolveJobDescription before = SolveJobDescription.of(job);
            repository.add(job);
            return before;
        });

        SolveJob loaded = transactions.inTransaction(() -> repository.get(id).orElseThrow());

        // Read outside the transaction, so a collection the load left unfetched fails here.
        assertThat(SolveJobDescription.of(loaded)).isEqualTo(added.at(Version.of(0)));
        assertThat(loaded.id()).isEqualTo(id);
        assertThat(loaded.plan()).isEqualTo(plan);
        assertThat(loaded.result()).isEqualTo(added.result());
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoJobWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(job(19)))).isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedJob_WhenEachPhaseIsSavedInItsOwnTransaction_ThenTheVersionMovesByOneAndTheEventCarriesIt() {
        JobId id = job(20);
        transactions.inTransaction(() -> repository.add(QueuedJobs.queue(id, plan(20), Lane.DAILY, REQUESTED)));
        long queued = storedVersion(id);

        changeAndSave(id, job -> job.claim(CLAIMED));
        long claimed = storedVersion(id);
        changeAndSave(id, job -> job.searchStarted(SEARCH));
        long searching = storedVersion(id);
        changeAndSave(id, job -> job.succeed(everyKindOfSolution(), ENDED));

        assertThat(List.of(queued, claimed, searching, storedVersion(id))).containsExactly(0L, 1L, 2L, 3L);
        assertThat(events.committed())
                .containsExactly(new SolveSucceeded(
                        new EventId("SolveSucceeded", id.value(), Version.of(3), 0), ENDED, id, plan(20), Lane.DAILY));
        assertThat(transactions.inTransaction(
                        () -> repository.get(id).orElseThrow().result()))
                .contains(resultOf(everyKindOfSolution()));
    }

    @Test
    void GivenTwoTransactionsOnOneJob_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        JobId id = job(21);
        transactions.inTransaction(() -> repository.add(QueuedJobs.queue(id, plan(21), Lane.DAILY, REQUESTED)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    SolveJob mine = repository.get(id).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        SolveJob theirs = repository.get(id).orElseThrow();
                        theirs.claim(CLAIMED);
                        repository.save(theirs);
                    });
                    mine.fail(SolveFailure.of(SolveFailureReason.QUEUED_TOO_LONG), ENDED);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("SolveJob");
                    assertThat(stale.id()).isEqualTo(id.value());
                });

        assertThat(storedVersion(id)).isEqualTo(1L);
        assertThat(transactions.inTransaction(
                        () -> repository.get(id).orElseThrow().phase()))
                .isEqualTo(new SolveJob.Phase.BuildingMatrix(CLAIMED));
    }

    // --- one queued or started re-optimization per plan (BR-38)

    @Test
    void GivenAPlansQueuedReoptimization_WhenAnotherIsAddedPastTheLock_ThenReoptimizationAlreadyRunning() {
        PlanId plan = plan(30);
        transactions.inTransaction(
                () -> repository.add(QueuedJobs.queue(job(30), plan, Lane.REOPTIMIZATION, REQUESTED)));
        SolveJob second = QueuedJobs.queue(job(31), plan, Lane.REOPTIMIZATION, REQUESTED);

        DomainAssertions.assertRefused(
                () -> transactions.inTransaction(() -> repository.add(second)), new ReoptimizationAlreadyRunning(plan));

        assertThat(transactions.inTransaction(() -> repository.get(job(31)))).isEmpty();
    }

    @Test
    void GivenAPlansStartedReoptimization_WhenAnotherIsAddedPastTheLock_ThenReoptimizationAlreadyRunning() {
        PlanId plan = plan(32);
        transactions.inTransaction(() -> repository.add(Scenario.REOPTIMIZATION_RUNNING.build(job(32), plan)));
        SolveJob second = QueuedJobs.queue(job(33), plan, Lane.REOPTIMIZATION, REQUESTED);

        DomainAssertions.assertRefused(
                () -> transactions.inTransaction(() -> repository.add(second)), new ReoptimizationAlreadyRunning(plan));
    }

    @Test
    void GivenAPlansEndedReoptimizationAndADailyJob_WhenAReoptimizationIsAdded_ThenItIsStored() {
        PlanId plan = plan(34);
        transactions.inTransaction(() -> {
            repository.add(Scenario.REOPTIMIZATION_SUCCEEDED.build(job(34), plan));
            repository.add(QueuedJobs.queue(job(35), plan, Lane.DAILY, REQUESTED));
        });

        transactions.inTransaction(
                () -> repository.add(QueuedJobs.queue(job(36), plan, Lane.REOPTIMIZATION, REQUESTED)));

        assertThat(transactions.inTransaction(() -> repository.get(job(36)))).isPresent();
    }

    @Test
    void GivenAStoredJob_WhenAJobWithItsIdIsAdded_ThenTheConstraintViolationPassesThrough() {
        transactions.inTransaction(() -> repository.add(QueuedJobs.queue(job(37), plan(37), Lane.DAILY, REQUESTED)));

        Throwable refused = catchThrowable(() -> transactions.inTransaction(
                () -> repository.add(QueuedJobs.queue(job(37), plan(38), Lane.DAILY, REQUESTED))));

        assertThat(refused).isNotInstanceOf(RuleViolation.class);
        assertThat(Stream.iterate(refused, Objects::nonNull, Throwable::getCause))
                .anyMatch(cause -> cause instanceof ConstraintViolationException violation
                        && "solve_job_pkey".equals(violation.getConstraintName()));
    }

    // --- the lane's queue and its lock (BR-112, BR-38, DL §5)

    @Test
    void GivenATransactionHoldingALanesCapacity_WhenAnotherReadsIt_ThenItCannotTakeTheLaneLockButTheOtherLaneIsFree() {
        transactions.inTransaction(() -> {
            assertThat(repository.capacityUnderLock(Lane.DAILY).lane()).isEqualTo(Lane.DAILY);
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        shortLockTimeout();
                        repository.capacityUnderLock(Lane.DAILY);
                    }))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            transactions.inANewTransaction(() -> {
                shortLockTimeout();
                assertThat(repository.capacityUnderLock(Lane.REOPTIMIZATION).lane())
                        .isEqualTo(Lane.REOPTIMIZATION);
            });
        });
    }

    /** A lock taken in a transaction of the call's own would be gone before the caller acts. */
    @Test
    void GivenNoTransaction_WhenALanesCapacityIsReadUnderLock_ThenTheCallIsRefused() {
        assertThatThrownBy(() -> repository.capacityUnderLock(Lane.DAILY))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void GivenFourQueuedDailyJobs_WhenTheCapacityIsRead_ThenAFifthQueuesAndASixthIsRefused() {
        inATransactionRolledBack(() -> {
            endEveryUnfinishedJob();
            for (int n = 40; n < 44; n++) {
                repository.add(QueuedJobs.queue(job(n), plan(n), Lane.DAILY, REQUESTED));
            }
            repository.add(QueuedJobs.queue(
                    job(44), plan(44), Lane.DAILY, repository.capacityUnderLock(Lane.DAILY), REQUESTED));
            LaneCapacity full = repository.capacityUnderLock(Lane.DAILY);

            DomainAssertions.assertRefused(
                    () -> QueuedJobs.queue(job(45), plan(45), Lane.DAILY, full, REQUESTED),
                    new SolveQueueFull(Lane.DAILY, LaneCapacity.QUEUE_LIMIT));
        });
    }

    @Test
    void GivenAJobBuildingItsMatrixOrRunning_WhenTheCapacityIsRead_ThenItCountsAsStartedNotQueued() {
        inATransactionRolledBack(() -> {
            endEveryUnfinishedJob();
            for (int n = 46; n < 50; n++) {
                repository.add(QueuedJobs.queue(job(n), plan(n), Lane.DAILY, REQUESTED));
            }
            repository.add(Scenario.DAILY_BUILDING_MATRIX.build(job(50), plan(50)));
            repository.add(Scenario.DAILY_RUNNING.build(job(51), plan(51)));
            LaneCapacity capacity = repository.capacityUnderLock(Lane.DAILY);

            assertThat(QueuedJobs.queue(job(52), plan(52), Lane.DAILY, capacity, REQUESTED))
                    .isNotNull();
        });
    }

    @Test
    void GivenAPlansQueuedOrStartedReoptimization_WhenTheCapacityIsRead_ThenItHoldsThatPlanAndNoOther() {
        inATransactionRolledBack(() -> {
            endEveryUnfinishedJob();
            repository.add(QueuedJobs.queue(job(53), plan(53), Lane.REOPTIMIZATION, REQUESTED));
            repository.add(Scenario.REOPTIMIZATION_RUNNING.build(job(54), plan(54)));
            repository.add(Scenario.REOPTIMIZATION_SUCCEEDED.build(job(55), plan(55)));
            repository.add(QueuedJobs.queue(job(56), plan(56), Lane.DAILY, REQUESTED));
            LaneCapacity capacity = repository.capacityUnderLock(Lane.REOPTIMIZATION);

            DomainAssertions.assertRefused(
                    () -> QueuedJobs.queue(job(57), plan(53), Lane.REOPTIMIZATION, capacity, REQUESTED),
                    new ReoptimizationAlreadyRunning(plan(53)));
            DomainAssertions.assertRefused(
                    () -> QueuedJobs.queue(job(58), plan(54), Lane.REOPTIMIZATION, capacity, REQUESTED),
                    new ReoptimizationAlreadyRunning(plan(54)));
            assertThat(QueuedJobs.queue(job(59), plan(55), Lane.REOPTIMIZATION, capacity, REQUESTED))
                    .isNotNull();
            assertThat(QueuedJobs.queue(job(60), plan(56), Lane.REOPTIMIZATION, capacity, REQUESTED))
                    .isNotNull();
        });
    }

    // --- claiming (ADR-051, TS §6)

    @Test
    void GivenTwoQueuedJobs_WhenTwoTransactionsEachClaimTheNext_ThenEachTakesADifferentJobOldestFirstWithoutWaiting() {
        JobId older = job(61);
        JobId newer = job(62);
        transactions.inTransaction(() -> {
            repository.add(QueuedJobs.queue(newer, plan(62), Lane.DAILY, LONG_AGO.plusSeconds(1)));
            repository.add(QueuedJobs.queue(older, plan(61), Lane.DAILY, LONG_AGO));
        });
        List<JobId> claimed = new ArrayList<>();

        transactions.inTransaction(() -> {
            SolveJob first = repository.claimNext(Lane.DAILY).orElseThrow();
            transactions.inANewTransaction(() -> {
                shortLockTimeout();
                SolveJob second = repository.claimNext(Lane.DAILY).orElseThrow();
                second.claim(CLAIMED);
                repository.save(second);
                claimed.add(second.id());
            });
            assertThat(first.phase()).isEqualTo(new SolveJob.Phase.Queued());
            first.claim(CLAIMED);
            repository.save(first);
            claimed.addFirst(first.id());
        });

        assertThat(claimed).containsExactly(older, newer);
        assertThat(storedPhase(older)).isEqualTo("BUILDING_MATRIX");
        assertThat(storedPhase(newer)).isEqualTo("BUILDING_MATRIX");
    }

    /** A lock taken in a transaction of the call's own would be gone before the caller acts. */
    @Test
    void GivenNoTransaction_WhenTheNextJobIsClaimed_ThenTheCallIsRefused() {
        assertThatThrownBy(() -> repository.claimNext(Lane.DAILY)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void GivenALaneWithNoQueuedJob_WhenItsNextJobIsClaimed_ThenThereIsNone() {
        inATransactionRolledBack(() -> {
            endEveryUnfinishedJob();
            repository.add(Scenario.DAILY_RUNNING.build(job(63), plan(63)));
            repository.add(QueuedJobs.queue(job(64), plan(64), Lane.REOPTIMIZATION, REQUESTED));

            assertThat(repository.claimNext(Lane.DAILY)).isEmpty();
            assertThat(repository.claimNext(Lane.REOPTIMIZATION).map(SolveJob::id))
                    .contains(job(64));
        });
    }

    // --- the unfinished jobs (BR-32, BR-74, BR-85, BR-112)

    @Test
    void GivenJobsInEveryPhase_WhenTheUnfinishedAreRead_ThenTheyAreTheQueuedAndStartedOnesOldestFirst() {
        inATransactionRolledBack(() -> {
            endEveryUnfinishedJob();
            repository.add(QueuedJobs.queue(job(65), plan(65), Lane.DAILY, REQUESTED.plusSeconds(3)));
            repository.add(Scenario.DAILY_SUCCEEDED.build(job(66), plan(66)));
            repository.add(Scenario.DAILY_REJECTED.build(job(67), plan(67)));
            repository.add(Scenario.REOPTIMIZATION_RUNNING.build(job(68), plan(68)));
            repository.add(QueuedJobs.queue(job(69), plan(69), Lane.REOPTIMIZATION, REQUESTED.minusSeconds(1)));
            repository.add(Scenario.DAILY_BUILDING_MATRIX.build(job(70), plan(70)));

            assertThat(repository.unfinished()).containsExactly(job(69), job(68), job(70), job(65));
        });
    }

    @Test
    void GivenNoUnfinishedJob_WhenTheUnfinishedAreRead_ThenThereAreNone() {
        inATransactionRolledBack(() -> {
            endEveryUnfinishedJob();
            repository.add(Scenario.DAILY_FAILED_IN_THE_QUEUE.build(job(71), plan(71)));

            assertThat(repository.unfinished()).isEmpty();
        });
    }

    // --- the unfinished jobs of a date's plans (BR-128)

    @Test
    void GivenJobsOfPlansOnTwoDates_WhenADatesUnfinishedAreRead_ThenTheyAreThatDatesQueuedAndStartedOnesOldestFirst() {
        DeliveryDate day = DeliveryDate.of(LocalDate.of(2040, 3, 1));
        DeliveryDate nextDay = DeliveryDate.of(day.value().plusDays(1));
        PlanId cancelled = plan(72);
        PlanId open = plan(73);
        PlanId later = plan(74);
        transactions.inTransaction(() -> plans.add(opened(cancelled, day)));
        jdbc.sql("UPDATE planning.plan SET status = 'CANCELLED', revision = 1, cancelled_at = opened_at WHERE id = ?")
                .param(cancelled.value())
                .update();
        transactions.inTransaction(() -> {
            plans.add(opened(open, day));
            plans.add(opened(later, nextDay));
        });
        transactions.inTransaction(() -> {
            repository.add(QueuedJobs.queue(job(72), open, Lane.DAILY, REQUESTED.plusSeconds(2)));
            repository.add(Scenario.REOPTIMIZATION_RUNNING.build(job(73), cancelled));
            repository.add(Scenario.DAILY_SUCCEEDED.build(job(74), open));
            repository.add(QueuedJobs.queue(job(75), later, Lane.DAILY, REQUESTED.minusSeconds(1)));
        });

        assertThat(transactions.inTransaction(() -> repository.unfinishedOn(day)))
                .containsExactly(job(73), job(72));
    }

    @Test
    void GivenADateWhosePlansHaveNoUnfinishedJob_WhenItsUnfinishedAreRead_ThenThereAreNone() {
        DeliveryDate day = DeliveryDate.of(LocalDate.of(2040, 3, 5));
        PlanId ended = plan(76);
        transactions.inTransaction(() -> plans.add(opened(ended, day)));
        transactions.inTransaction(() -> repository.add(Scenario.DAILY_REJECTED.build(job(76), ended)));

        assertThat(transactions.inTransaction(() -> repository.unfinishedOn(day)))
                .isEmpty();
        assertThat(transactions.inTransaction(() ->
                        repository.unfinishedOn(DeliveryDate.of(day.value().plusDays(1)))))
                .isEmpty();
    }

    private static Plan opened(PlanId plan, DeliveryDate date) {
        return Plan.open(plan, date, PlansOnDate.none(), date.value(), REQUESTED);
    }

    // --- the load check (ADR-127 decision 1), one case for each damaged state the domain tests used to build

    static Stream<Arguments> damagedRows() {
        String basis = "a basis revision belongs to a re-optimization, and only to one";
        String limit = "a re-optimization's limit is 20 seconds";
        String result = "a result belongs to a succeeded daily job, and only to one";
        String times = "the phase's times are out of order";
        String violationsMatch = "violations are listed exactly for a rejection or an unhonoured lock";
        String refused = "a stored value is refused with ";
        // The domain's "succeeded daily job without its result" has no stored counterpart: a result's
        // presence follows the lane and the phase, so such a job loads with the result it stores.
        return Stream.of(
                Arguments.of(
                        "a re-optimization limit other than 20 s",
                        1001,
                        Scenario.REOPTIMIZATION_QUEUED,
                        (Damage) rows -> rows.update("solve_job", "solve_limit = 45000000000"),
                        "SolveJob",
                        limit),
                Arguments.of(
                        "a queued job with a result",
                        1002,
                        Scenario.DAILY_QUEUED,
                        (Damage) rows -> rows.run("""
                                INSERT INTO planning.solve_job_excluded_vehicle (solve_job_id, position, vehicle_id, cause)
                                VALUES (?, 0, gen_random_uuid(), 'NO_DRIVER')
                                """),
                        "SolveJob",
                        result),
                Arguments.of(
                        "a re-optimization that succeeded with a result",
                        1003,
                        Scenario.REOPTIMIZATION_SUCCEEDED,
                        (Damage) rows -> rows.run("""
                                INSERT INTO planning.solve_job_route (solve_job_id, position, vehicle_id, driver_id)
                                VALUES (?, 0, gen_random_uuid(), gen_random_uuid())
                                """),
                        "SolveJob",
                        result),
                Arguments.of(
                        "a claim before the request",
                        1004,
                        Scenario.DAILY_BUILDING_MATRIX,
                        (Damage) rows -> rows.update("solve_job", "claimed_at = requested_at - interval '1 second'"),
                        "SolveJob",
                        times),
                Arguments.of(
                        "a search before the claim",
                        1005,
                        Scenario.DAILY_RUNNING,
                        (Damage) rows ->
                                rows.update("solve_job", "search_started_at = claimed_at - interval '1 second'"),
                        "SolveJob",
                        times),
                Arguments.of(
                        "an end before the search",
                        1006,
                        Scenario.DAILY_SUCCEEDED,
                        (Damage) rows -> rows.update("solve_job", "ended_at = search_started_at - interval '1 second'"),
                        "SolveJob",
                        times),
                Arguments.of(
                        "a failure before the request",
                        1007,
                        Scenario.DAILY_FAILED_IN_THE_QUEUE,
                        (Damage) rows -> rows.update("solve_job", "ended_at = requested_at - interval '1 second'"),
                        "SolveJob",
                        times),
                Arguments.of(
                        "a rejection without its violations",
                        1008,
                        Scenario.DAILY_REJECTED,
                        (Damage) rows -> rows.run("DELETE FROM planning.solve_job_violation WHERE solve_job_id = ?"),
                        "SolveFailure",
                        violationsMatch),
                Arguments.of(
                        "violations on a failure that lists none",
                        1009,
                        Scenario.DAILY_FAILED_IN_THE_QUEUE,
                        (Damage) rows -> rows.violation(0),
                        "SolveFailure",
                        violationsMatch),
                Arguments.of(
                        "violations on a job that hasn't failed",
                        1010,
                        Scenario.DAILY_RUNNING,
                        (Damage) rows -> rows.violation(0),
                        "SolveJob",
                        "violations belong to a failed job"),
                Arguments.of(
                        "a stop on no route of the result",
                        1011,
                        Scenario.DAILY_SUCCEEDED,
                        (Damage) rows -> rows.update("solve_job_stop", "route_position = route_position + 10"),
                        "SolvedRoute",
                        "a solved stop is on one of its solution's routes"),
                Arguments.of(
                        "a route's stops stored out of their order",
                        1012,
                        Scenario.DAILY_SUCCEEDED,
                        (Damage) rows -> rows.update(
                                "solve_job_stop", "stop_position = 1 - stop_position", "route_position = 0"),
                        "SolvedRoute",
                        "a solved route's stops are stored in their order"),
                Arguments.of(
                        "a stop served before it is reached",
                        1013,
                        Scenario.DAILY_SUCCEEDED,
                        (Damage) rows -> rows.update("solve_job_stop", "start_seconds = arrival_seconds - 1"),
                        "StopPlan",
                        refused + "INVALID_STOP_TIMES"),
                Arguments.of(
                        "a route that returns before it leaves",
                        1014,
                        Scenario.DAILY_SUCCEEDED,
                        (Damage) rows -> rows.update(
                                "solve_job_route",
                                "returns_at_seconds = leaves_at_seconds - 1",
                                "times_date IS NOT NULL"),
                        "RoutePlan",
                        refused + "INVALID_ROUTE_TIMES"),
                Arguments.of(
                        "a drop's earliest time before its day",
                        1015,
                        Scenario.DAILY_SUCCEEDED,
                        (Damage) rows -> rows.update("solve_job_drop", "earliest_seconds = -1", "reason = 'SHIFT'"),
                        "DroppedOrder",
                        refused + "TIME_BEFORE_THE_DAY"),
                Arguments.of(
                        "a drop's limit before its day",
                        1016,
                        Scenario.DAILY_SUCCEEDED,
                        (Damage) rows -> rows.update("solve_job_drop", "fact_limit_seconds = -1", "reason = 'SHIFT'"),
                        "DaySeconds",
                        refused + "OUTSIDE_THE_DAY"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredJobBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Scenario scenario, Damage damage, String subject, String detail) {
        JobId id = job(n);
        transactions.inTransaction(() -> repository.add(scenario.build(id, plan(n))));

        damage.apply(new Rows(jdbc, id));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(id)))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    static Stream<Arguments> damagedColumns() {
        return Stream.of(
                Arguments.of(
                        "a rule code that is no row ID",
                        1101,
                        Scenario.DAILY_REJECTED,
                        (Damage) rows -> rows.update("solve_job_violation", "rule = 'not a rule'"),
                        "RuleCode"),
                Arguments.of(
                        "a basis revision below 1",
                        1102,
                        Scenario.REOPTIMIZATION_QUEUED,
                        (Damage) rows -> rows.update("solve_job", "basis_revision = 0"),
                        "RevisionNo"),
                Arguments.of(
                        "a solver that is no solver's name",
                        1103,
                        Scenario.DAILY_QUEUED,
                        (Damage) rows -> rows.update("solve_job", "solver = 'OR Tools'"),
                        "SolverName"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedColumns")
    void GivenAStoredColumnItsValueRefuses_WhenTheJobIsLoaded_ThenItsConverterRaisesStoredStateInvalid(
            String broken, int n, Scenario scenario, Damage damage, String subject) {
        JobId id = job(n);
        transactions.inTransaction(() -> repository.add(scenario.build(id, plan(n))));

        damage.apply(new Rows(jdbc, id));

        // Hibernate wraps a converter's exception, and Spring wraps Hibernate's.
        Throwable thrown = catchThrowable(() -> transactions.inTransaction(() -> repository.get(id)));
        assertThat(storedStateInvalidIn(thrown).subject()).isEqualTo(subject);
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("a basis revision on a daily job", 1201, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update("solve_job", "basis_revision = 1")),
                Arguments.of(
                        "a re-optimization without a basis revision", 1202, Scenario.REOPTIMIZATION_QUEUED, (Damage)
                                rows -> rows.update("solve_job", "basis_revision = NULL")),
                Arguments.of("a lane no job has", 1203, Scenario.DAILY_QUEUED, (Damage)
                        rows -> rows.update("solve_job", "lane = 'NIGHTLY'")),
                Arguments.of("a phase no job has", 1204, Scenario.DAILY_QUEUED, (Damage)
                        rows -> rows.update("solve_job", "phase = 'PAUSED'")),
                Arguments.of("a queued job with a claim", 1205, Scenario.DAILY_QUEUED, (Damage)
                        rows -> rows.update("solve_job", "claimed_at = requested_at")),
                Arguments.of("a running job without its search start", 1206, Scenario.DAILY_RUNNING, (Damage)
                        rows -> rows.update("solve_job", "search_started_at = NULL")),
                Arguments.of("a succeeded job without its end", 1207, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update("solve_job", "ended_at = NULL")),
                Arguments.of("a failed job without its reason", 1208, Scenario.DAILY_REJECTED, (Damage)
                        rows -> rows.update("solve_job", "failure_reason = NULL")),
                Arguments.of("a reason no failure has", 1209, Scenario.DAILY_REJECTED, (Damage)
                        rows -> rows.update("solve_job", "failure_reason = 'TIRED'")),
                Arguments.of("a route plan missing its return", 1210, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update("solve_job_route", "returns_at_seconds = NULL")),
                Arguments.of("a reason no drop has", 1211, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update("solve_job_drop", "reason = 'LOST'")),
                Arguments.of(
                        "a pin drop without its target", 1212, Scenario.DAILY_SUCCEEDED, (Damage) rows -> rows.update(
                                "solve_job_drop",
                                "pin_vehicle_id = NULL, pin_driver_id = NULL",
                                "reason = 'PIN_NOT_HONOURED'")),
                Arguments.of("a pin drop naming a vehicle and a driver", 1213, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update(
                                "solve_job_drop",
                                "pin_vehicle_id = gen_random_uuid(), pin_driver_id = gen_random_uuid()",
                                "reason = 'PIN_NOT_HONOURED'")),
                Arguments.of("a capacity drop missing a figure", 1214, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update("solve_job_drop", "volume = NULL", "reason = 'CAPACITY'")),
                Arguments.of(
                        "a displaced drop with a weight", 1215, Scenario.DAILY_SUCCEEDED, (Damage) rows -> rows.update(
                                "solve_job_drop",
                                "weight = 1, volume = 1, largest_weight_capacity = 1, largest_volume_capacity = 1",
                                "reason = 'DISPLACED'")),
                Arguments.of("a time window drop without its fact", 1216, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update(
                                "solve_job_drop",
                                "fact_limit_date = NULL, fact_limit_seconds = NULL, earliest_date = NULL, earliest_seconds = NULL",
                                "reason = 'TIME_WINDOW'")),
                Arguments.of("a cause no exclusion has", 1217, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update("solve_job_excluded_vehicle", "cause = 'BORED'")),
                Arguments.of("two stops at one place on a route", 1218, Scenario.DAILY_SUCCEEDED, (Damage)
                        rows -> rows.update("solve_job_stop", "stop_position = 0")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredJob_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Scenario scenario, Damage damage) {
        JobId id = job(n);
        transactions.inTransaction(() -> repository.add(scenario.build(id, plan(n))));
        Rows rows = new Rows(jdbc, id);

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(id))).isPresent();
    }

    /** One damage done with SQL to a stored job's rows. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run on one job's rows; every statement is the test's own constant. */
    record Rows(JdbcClient jdbc, JobId job) {

        /** Sets columns of the job's rows in one of Planning's solve job tables. */
        void update(String table, String assignments) {
            update(table, assignments, "TRUE");
        }

        /** Sets columns of those of the job's rows in one of its tables that meet a condition. */
        void update(String table, String assignments, String condition) {
            String key = table.equals("solve_job") ? "id" : "solve_job_id";
            jdbc.sql("UPDATE planning." + table + " SET " + assignments + " WHERE " + key + " = ? AND (" + condition
                            + ")")
                    .param(job.value())
                    .update();
        }

        /** Stores a violation of BR-05 on a route at a position of the job's violations. */
        void violation(int position) {
            jdbc.sql("""
                            INSERT INTO planning.solve_job_violation (solve_job_id, position, rule, route_id)
                            VALUES (?, ?, 'BR-05', gen_random_uuid())
                            """).params(job.value(), position).update();
        }

        /** Runs a statement whose one parameter is the job's UUID. */
        void run(String statement) {
            jdbc.sql(statement).param(job.value()).update();
        }
    }

    private static SolveResult resultOf(ValidatedSolution solution) {
        return new SolveResult(solution.routes(), solution.dropped(), solution.excluded());
    }

    // --- helpers

    private void changeAndSave(JobId id, Consumer<SolveJob> change) {
        transactions.inTransaction(() -> {
            SolveJob job = repository.get(id).orElseThrow();
            change.accept(job);
            repository.save(job);
        });
    }

    /** Runs work in a transaction that is rolled back, so what it stores is seen by no other test. */
    private void inATransactionRolledBack(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            work.run();
            status.setRollbackOnly();
        });
    }

    /**
     * Ends every job another test left queued or started, inside a transaction that is rolled back,
     * so a read of a lane's queue sees only the jobs the test stores next.
     */
    private void endEveryUnfinishedJob() {
        jdbc.sql("""
                UPDATE planning.solve_job
                SET phase = 'FAILED', failure_reason = 'INTERRUPTED', ended_at = requested_at,
                    claimed_at = NULL, search_started_at = NULL
                WHERE phase IN ('QUEUED', 'BUILDING_MATRIX', 'RUNNING')
                """).update();
    }

    private void shortLockTimeout() {
        jdbc.sql("SET LOCAL lock_timeout = '200ms'").update();
    }

    private long storedVersion(JobId job) {
        return jdbc.sql("SELECT version FROM planning.solve_job WHERE id = ?")
                .param(job.value())
                .query(Long.class)
                .single();
    }

    private String storedPhase(JobId job) {
        return jdbc.sql("SELECT phase FROM planning.solve_job WHERE id = ?")
                .param(job.value())
                .query(String.class)
                .single();
    }

    private static StoredStateInvalid storedStateInvalidIn(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof StoredStateInvalid invalid) {
                return invalid;
            }
        }
        throw new AssertionError("no StoredStateInvalid in the cause chain of " + thrown);
    }

    // --- keys

    private static JobId job(int n) {
        return JobId.of(uuid(n));
    }

    private static PlanId plan(int n) {
        return PlanId.of(uuid(100_000 + n));
    }

    private static UUID uuid(long n) {
        return new UUID(KEY_PREFIX, n);
    }
}
