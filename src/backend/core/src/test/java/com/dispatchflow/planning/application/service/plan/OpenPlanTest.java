package com.dispatchflow.planning.application.service.plan;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.TestActors;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.service.AppendedAuditRecords;
import com.dispatchflow.audit.application.service.AuditLogService;
import com.dispatchflow.audit.domain.AuditOutcome;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.audit.domain.NamedRecord;
import com.dispatchflow.planning.application.port.out.RecordedLiveUpdates;
import com.dispatchflow.planning.application.port.out.RecordedLiveUpdates.PlanChanged;
import com.dispatchflow.planning.application.port.out.plan.InMemoryPlanRepository;
import com.dispatchflow.planning.domain.PlanningError.DateInPast;
import com.dispatchflow.planning.domain.PlanningError.PlanAlreadyExists;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.SharedKernelError.DateOutOfRange;
import com.dispatchflow.shared.application.FixedClock;
import com.dispatchflow.shared.application.SequentialIds;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Opening a date's plan (FR-15, BR-34, BR-118), against in-memory fakes of Planning's ports and
 * the real audit log over an in-memory store: the change adds the plan and records its success,
 * and the use case records a refusal once the change has refused, then tells the open screens only
 * of a success (ADR-129, ADR-149, ADR-150).
 */
class OpenPlanTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 15);
    private static final DeliveryDate TOMORROW = DeliveryDate.of(TODAY.plusDays(1));
    private static final DeliveryDate YESTERDAY = DeliveryDate.of(TODAY.minusDays(1));
    private static final Instant NOW = Instant.parse("2027-06-15T05:00:00.123456Z");
    private static final long ID_PREFIX = 0x0193a4b27c1d7e00L;
    private static final PlanId FIRST_NEW_PLAN = PlanId.of(SequentialIds.nth(ID_PREFIX, 1));

    private final Actor dispatcher = TestActors.dispatcher();
    private final InMemoryPlanRepository plans = new InMemoryPlanRepository();
    private final AppendedAuditRecords appended = new AppendedAuditRecords();
    private final RecordedLiveUpdates liveUpdates = new RecordedLiveUpdates();

    private OpenPlan openPlan() {
        FixedClock clock = new FixedClock(NOW, TODAY);
        AuditLogService auditLog = new AuditLogService(appended);
        OpenPlanChange change = new OpenPlanChange(plans, clock, new SequentialIds(ID_PREFIX), auditLog);
        return new OpenPlan(liveUpdates, change, auditLog, clock);
    }

    @Test
    void GivenADateWithNoPlan_WhenOpen_ThenADraftPlanIsAdded() {
        OpenedPlan opened = openPlan().open(dispatcher, TOMORROW.value());

        assertThat(opened.plan()).isEqualTo(FIRST_NEW_PLAN);
        assertThat(plans.all()).singleElement().satisfies(plan -> {
            assertThat(plan.id()).isEqualTo(FIRST_NEW_PLAN);
            assertThat(plan.date()).isEqualTo(TOMORROW);
            assertThat(plan.status()).isEqualTo(new Plan.Status.Draft());
        });
    }

    @Test
    void GivenTodaysDate_WhenOpen_ThenADraftPlanIsAdded() {
        DeliveryDate today = DeliveryDate.of(TODAY);

        OpenedPlan opened = openPlan().open(dispatcher, TODAY);

        assertThat(plans.get(opened.plan()))
                .hasValueSatisfying(plan -> assertThat(plan.date()).isEqualTo(today));
    }

    @Test
    void GivenANewPlan_WhenOpen_ThenItAnswersTheVersionItsAddStored() {
        OpenedPlan opened = openPlan().open(dispatcher, TOMORROW.value());

        assertThat(plans.get(opened.plan()))
                .hasValueSatisfying(plan -> assertThat(opened.version()).isEqualTo(plan.version()));
    }

    @Test
    void GivenANewPlan_WhenOpen_ThenOneSucceededRecordNamesThePlan() {
        openPlan().open(dispatcher, TOMORROW.value());

        assertRecorded(AuditOutcome.succeeded(), List.of(NamedRecord.of(FIRST_NEW_PLAN)));
    }

    @Test
    void GivenANewPlan_WhenOpen_ThenLiveUpdatesGetsPlanChanged() {
        openPlan().open(dispatcher, TOMORROW.value());

        assertThat(liveUpdates.sent()).containsExactly(new PlanChanged(FIRST_NEW_PLAN));
    }

    @Test
    void GivenAPastDate_WhenOpen_ThenDateInPast() {
        DateInPast refusal = new DateInPast(YESTERDAY);

        assertRefused(() -> openPlan().open(dispatcher, YESTERDAY.value()), refusal);

        assertThat(plans.all()).isEmpty();
        assertRefusalRecordedAndNothingSent(refusal);
    }

    /**
     * A year outside 1 to 9999 is no delivery date. The request's date becomes one inside the
     * recorded attempt, so its refusal is recorded too.
     */
    @Test
    void GivenAYearPast9999_WhenOpen_ThenDateOutOfRange() {
        LocalDate farOff = LocalDate.of(10_000, 1, 1);
        DateOutOfRange refusal = new DateOutOfRange(farOff);

        assertRefused(() -> openPlan().open(dispatcher, farOff), refusal);

        assertThat(plans.all()).isEmpty();
        assertRefusalRecordedAndNothingSent(refusal);
    }

    @Test
    void GivenADateWithAPlan_WhenOpen_ThenPlanAlreadyExists() {
        Plan existing = aPlan().draft();
        plans.holding(existing);
        PlanAlreadyExists refusal = new PlanAlreadyExists(existing.date(), existing.id());

        assertRefused(() -> openPlan().open(dispatcher, existing.date().value()), refusal);

        assertThat(plans.all()).containsExactly(existing);
        assertRefusalRecordedAndNothingSent(refusal);
    }

    @Test
    void GivenADateWhosePlanWasCancelled_WhenOpen_ThenADraftPlanIsAdded() {
        Plan cancelled = aPlan().cancelled();
        plans.holding(cancelled);

        OpenedPlan opened = openPlan().open(dispatcher, cancelled.date().value());

        assertThat(plans.get(opened.plan()))
                .hasValueSatisfying(plan -> assertThat(plan.status()).isEqualTo(new Plan.Status.Draft()));
    }

    /** A refusal names no record, since no plan was made, and the open screens are told nothing. */
    private void assertRefusalRecordedAndNothingSent(DomainError refusal) {
        assertRecorded(AuditOutcome.refused(refusal), List.of());
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
