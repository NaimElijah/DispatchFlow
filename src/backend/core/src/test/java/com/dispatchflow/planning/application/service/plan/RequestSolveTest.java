package com.dispatchflow.planning.application.service.plan;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.TestActors;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.service.AppendedAuditRecords;
import com.dispatchflow.audit.application.service.AuditLogService;
import com.dispatchflow.audit.domain.AuditOutcome;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.audit.domain.ErrorCode;
import com.dispatchflow.audit.domain.NamedRecord;
import com.dispatchflow.planning.application.port.out.RecordedLiveUpdates;
import com.dispatchflow.planning.application.port.out.RecordedLiveUpdates.JobChanged;
import com.dispatchflow.planning.application.port.out.RecordedLiveUpdates.PlanChanged;
import com.dispatchflow.planning.application.port.out.SequentialSeeds;
import com.dispatchflow.planning.application.port.out.plan.InMemoryPlanRepository;
import com.dispatchflow.planning.application.port.out.settings.InMemoryPlanningSettingsRepository;
import com.dispatchflow.planning.application.port.out.solvejob.InMemorySolveJobRepository;
import com.dispatchflow.planning.application.service.SolverNotOffered;
import com.dispatchflow.planning.domain.PlanningError.DateInPast;
import com.dispatchflow.planning.domain.PlanningError.PlanNotDraftOrReady;
import com.dispatchflow.planning.domain.PlanningError.SolveQueueFull;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.basis.SolveBasis;
import com.dispatchflow.planning.domain.plan.LaneCapacity;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.plan.QueuedJobs;
import com.dispatchflow.planning.domain.plan.Seed;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.planning.domain.settings.PlanningSettings;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.FixedClock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.SequentialIds;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * Requesting a daily solve (FR-15, FR-16, FR-19, FR-20, BR-76, BR-112, BR-118, DL §6.1), against
 * in-memory fakes of Planning's ports and the real audit log over an in-memory store: the change
 * makes the plan solving, queues the job and records its success, and the use case refuses a solver
 * other than OR-Tools before it, records each refusal once the change has refused, and tells the
 * open screens only of a success (ADR-129, ADR-149, ADR-150).
 */
class RequestSolveTest {

    /** The date of {@code aPlan()}'s plans, so none of them is past. */
    private static final LocalDate PLANS_DATE = LocalDate.of(2027, 6, 15);

    private static final Instant NOW = Instant.parse("2027-06-15T05:00:00.123456Z");
    private static final long ID_PREFIX = 0x0193a4b27c1d7e00L;
    private static final JobId FIRST_NEW_JOB = JobId.of(SequentialIds.nth(ID_PREFIX, 1));
    private static final long FIRST_SEED = 4_242;
    private static final SolveLimit THIRTY_SECONDS = SolveLimit.daily(Duration.ofSeconds(30));
    private static final long OTHER_PLANS_PREFIX = 0x0193a4b27c1d7f00L;

    private final Actor dispatcher = TestActors.dispatcher();
    private final InMemoryPlanRepository plans = new InMemoryPlanRepository();
    private final InMemorySolveJobRepository jobs = new InMemorySolveJobRepository();
    private final AppendedAuditRecords appended = new AppendedAuditRecords();
    private final RecordedLiveUpdates liveUpdates = new RecordedLiveUpdates();
    private InMemoryPlanningSettingsRepository settings = new InMemoryPlanningSettingsRepository();
    private LocalDate today = PLANS_DATE;

    private RequestSolve requestSolve() {
        FixedClock clock = new FixedClock(NOW, today);
        AuditLogService auditLog = new AuditLogService(appended);
        RequestSolveChange change = new RequestSolveChange(
                plans, jobs, settings, new SequentialSeeds(FIRST_SEED), clock, new SequentialIds(ID_PREFIX), auditLog);
        return new RequestSolve(liveUpdates, change, auditLog, clock);
    }

    private Plan aDraftPlan() {
        Plan draft = aPlan().draft();
        plans.holding(draft);
        return draft;
    }

    private JobId request(Plan plan, Optional<SolverName> solver, Optional<SolveLimit> limit) {
        return requestSolve().request(dispatcher, plan.id(), solver, limit, plan.version());
    }

    @Test
    void GivenADraftPlan_WhenRequestSolve_ThenItIsSolvingAndAJobIsQueued() {
        Plan plan = aDraftPlan();
        Version loaded = plan.version();

        JobId job = request(plan, Optional.empty(), Optional.of(THIRTY_SECONDS));

        assertThat(job).isEqualTo(FIRST_NEW_JOB);
        assertThat(plan.status()).isEqualTo(new Plan.Status.Solving(new SolveBasis(job, NOW)));
        assertThat(plan.version()).isEqualTo(loaded.next());
        assertThat(jobs.all()).singleElement().satisfies(queued -> {
            assertThat(queued.id()).isEqualTo(job);
            assertThat(queued.plan()).isEqualTo(plan.id());
            assertThat(queued.lane()).isEqualTo(Lane.DAILY);
            assertThat(queued.phase()).isEqualTo(new SolveJob.Phase.Queued());
            assertThat(queued.solver()).isEqualTo(SolverName.OR_TOOLS);
            assertThat(queued.seed()).isEqualTo(Seed.of(FIRST_SEED));
            assertThat(queued.requestedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void GivenALimit_WhenRequestSolve_ThenTheJobHasIt() {
        request(aDraftPlan(), Optional.empty(), Optional.of(THIRTY_SECONDS));

        assertThat(jobs.all())
                .singleElement()
                .satisfies(job -> assertThat(job.limit()).isEqualTo(THIRTY_SECONDS));
    }

    @Test
    void GivenNoLimit_WhenRequestSolve_ThenTheJobHas45Seconds() {
        request(aDraftPlan(), Optional.empty(), Optional.empty());

        assertThat(jobs.all())
                .singleElement()
                .satisfies(job -> assertThat(job.limit()).isEqualTo(SolveLimit.daily(Duration.ofSeconds(45))));
    }

    @Test
    void GivenSettingsWithAnotherDefaultLimit_WhenRequestSolveWithNoLimit_ThenTheJobHasTheSettingsDefault() {
        PlanningSettings changed = PlanningSettings.startingValues();
        changed.changeDefaultSolveLimit(THIRTY_SECONDS);
        settings = new InMemoryPlanningSettingsRepository(changed);

        request(aDraftPlan(), Optional.empty(), Optional.empty());

        assertThat(jobs.all())
                .singleElement()
                .satisfies(job -> assertThat(job.limit()).isEqualTo(THIRTY_SECONDS));
    }

    @Test
    void GivenOrToolsNamed_WhenRequestSolve_ThenTheJobRunsOrTools() {
        request(aDraftPlan(), Optional.of(SolverName.OR_TOOLS), Optional.empty());

        assertThat(jobs.all())
                .singleElement()
                .satisfies(job -> assertThat(job.solver()).isEqualTo(SolverName.OR_TOOLS));
    }

    @Test
    void GivenAReadyPlan_WhenRequestSolve_ThenItIsSolvingAgain() {
        Plan ready = aPlan().ready();
        plans.holding(ready);

        JobId job = request(ready, Optional.empty(), Optional.empty());

        assertThat(ready.status()).isEqualTo(new Plan.Status.Solving(new SolveBasis(job, NOW)));
    }

    @Test
    void GivenADraftPlan_WhenRequestSolve_ThenOneSucceededRecordNamesThePlanAndTheJob() {
        Plan plan = aDraftPlan();

        JobId job = request(plan, Optional.empty(), Optional.empty());

        assertRecorded(AuditOutcome.succeeded(), List.of(NamedRecord.of(plan.id()), NamedRecord.of(job)));
    }

    @Test
    void GivenADraftPlan_WhenRequestSolve_ThenLiveUpdatesGetsJobChangedAndPlanChanged() {
        Plan plan = aDraftPlan();

        JobId job = request(plan, Optional.empty(), Optional.empty());

        assertThat(liveUpdates.sent())
                .containsExactlyInAnyOrder(new JobChanged(job, plan.id()), new PlanChanged(plan.id()));
    }

    @Test
    void GivenAStaleVersion_WhenRequestSolve_ThenStaleVersion() {
        Plan plan = aDraftPlan();
        Version stale = plan.version().next();

        assertThatThrownBy(
                        () -> requestSolve().request(dispatcher, plan.id(), Optional.empty(), Optional.empty(), stale))
                .isInstanceOfSatisfying(StaleVersion.class, refusal -> {
                    assertThat(refusal.kind()).isEqualTo("Plan");
                    assertThat(refusal.id()).isEqualTo(plan.id().value());
                    assertThat(refusal.comparedTheRequestsVersion()).isTrue();
                });

        assertThat(plan.status()).isEqualTo(new Plan.Status.Draft());
        assertThat(jobs.all()).isEmpty();
        // A race is the call wrapper's to record, once it answers it (ADR-140 decision 6).
        assertThat(appended.records()).isEmpty();
        assertThat(liveUpdates.sent()).isEmpty();
    }

    @Test
    void GivenFiveQueuedJobs_WhenRequestSolve_ThenSolveQueueFull() {
        Plan plan = aDraftPlan();
        LongStream.rangeClosed(1, LaneCapacity.QUEUE_LIMIT)
                .forEach(n -> jobs.add(QueuedJobs.queue(
                        JobId.of(new UUID(OTHER_PLANS_PREFIX, n)),
                        PlanId.of(new UUID(OTHER_PLANS_PREFIX, 100 + n)),
                        Lane.DAILY,
                        NOW)));
        SolveQueueFull refusal = new SolveQueueFull(Lane.DAILY, LaneCapacity.QUEUE_LIMIT);

        assertRefused(() -> request(plan, Optional.empty(), Optional.empty()), refusal);

        assertThat(jobs.all()).hasSize(LaneCapacity.QUEUE_LIMIT);
        assertRefusalRecordedAndNothingSent(AuditOutcome.refused(refusal), plan.id());
    }

    @Test
    void GivenAnUnknownPlan_WhenRequestSolve_ThenNotFound() {
        PlanId unknown = PlanId.of(new UUID(OTHER_PLANS_PREFIX, 1));

        assertThatThrownBy(() ->
                        requestSolve().request(dispatcher, unknown, Optional.empty(), Optional.empty(), Version.of(0)))
                .isInstanceOfSatisfying(NotFound.class, refusal -> {
                    assertThat(refusal.kind()).isEqualTo("Plan");
                    assertThat(refusal.id()).isEqualTo(unknown.value());
                });

        assertThat(jobs.all()).isEmpty();
        assertRefusalRecordedAndNothingSent(AuditOutcome.refused(ErrorCode.of(NotFound.CODE)), unknown);
    }

    @Test
    void GivenASolvingPlan_WhenRequestSolve_ThenPlanNotDraftOrReady() {
        Plan solving = aPlan().solving();
        plans.holding(solving);
        PlanNotDraftOrReady refusal = new PlanNotDraftOrReady(solving.id());

        assertRefused(() -> request(solving, Optional.empty(), Optional.empty()), refusal);

        assertThat(jobs.all()).isEmpty();
        assertRefusalRecordedAndNothingSent(AuditOutcome.refused(refusal), solving.id());
    }

    @Test
    void GivenAPlanOfAPastDate_WhenRequestSolve_ThenDateInPast() {
        Plan plan = aDraftPlan();
        today = PLANS_DATE.plusDays(1);
        DateInPast refusal = new DateInPast(plan.date());

        assertRefused(() -> request(plan, Optional.empty(), Optional.empty()), refusal);

        assertThat(plan.status()).isEqualTo(new Plan.Status.Draft());
        assertThat(jobs.all()).isEmpty();
        assertRefusalRecordedAndNothingSent(AuditOutcome.refused(refusal), plan.id());
    }

    @Test
    void GivenASolverOtherThanOrTools_WhenRequestSolve_ThenSolverNotOffered() {
        Plan plan = aDraftPlan();
        SolverName pyvrp = SolverName.of("pyvrp");

        assertThatThrownBy(() -> request(plan, Optional.of(pyvrp), Optional.empty()))
                .isInstanceOfSatisfying(
                        SolverNotOffered.class,
                        refusal -> assertThat(refusal.solver()).isEqualTo(pyvrp));

        assertThat(plan.status()).isEqualTo(new Plan.Status.Draft());
        assertThat(jobs.all()).isEmpty();
        assertRefusalRecordedAndNothingSent(AuditOutcome.refused(ErrorCode.of(SolverNotOffered.CODE)), plan.id());
    }

    /** A refusal names the plan the request named, and the open screens are told nothing. */
    private void assertRefusalRecordedAndNothingSent(AuditOutcome refused, PlanId plan) {
        assertRecorded(refused, List.of(NamedRecord.of(plan)));
        assertThat(liveUpdates.sent()).isEmpty();
    }

    private void assertRecorded(AuditOutcome outcome, List<NamedRecord> named) {
        AuditRecord record = appended.only();
        assertThat(record.outcome()).isEqualTo(outcome);
        assertThat(record.records()).isEqualTo(named);
        assertThat(record.at()).isEqualTo(NOW);
        assertThat(record.user()).contains(dispatcher.account());
    }
}
