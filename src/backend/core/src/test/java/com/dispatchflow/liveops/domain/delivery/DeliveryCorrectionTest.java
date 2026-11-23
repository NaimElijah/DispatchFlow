package com.dispatchflow.liveops.domain.delivery;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DATE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER_STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NO_HOLD;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_ROUTE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ORDER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SECOND;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.TYPO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0900;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.aDelivery;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.assertRefusedUnchanged;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.reload;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.liveops.domain.LiveOpsError.CorrectionChangesNothing;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.Origin;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.OutcomeRecord;
import com.dispatchflow.liveops.domain.outcome.DispatcherOutcome;
import com.dispatchflow.liveops.domain.outcome.FailureReason;
import com.dispatchflow.liveops.domain.outcome.FailureReasonKind;
import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.liveops.events.PutBackSuperseded;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Corrections (FR-333): the original stays beside each correction, figures use the latest, the time
 * a corrected outcome counts from (ADR-107), what a correction may change alone (ADR-114), and
 * FR-404's correction after a put-back. A correction
 * raises no event of its own, because nothing reacts to one (FR-374, ADR-064).
 */
class DeliveryCorrectionTest {

    private static final DeliveryDate PUT_BACK_DATE = DeliveryDate.of(LocalDate.of(2027, 3, 3));

    private static void correctToDelivered(Delivery delivery, Optional<Instant> stated, LaterAttempt later) {
        delivery.correct(
                new DispatcherOutcome.Delivered(false),
                TYPO,
                stated,
                DISPATCHER,
                later,
                LeaveAtDoor.NOT_ALLOWED,
                LATER);
    }

    private static void correctToFailed(Delivery delivery, FailureReasonKind kind, LaterAttempt later) {
        delivery.correct(
                new DispatcherOutcome.Failed(FailureReason.of(kind)),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                later,
                LeaveAtDoor.NOT_ALLOWED,
                LATER);
    }

    private static Delivery failedWithOther(String text) {
        Delivery delivery = aDelivery().fresh();
        delivery.fail(byA(ONLINE), FailureReason.other(text), ON_ROUTE, NO_HOLD, HELD_1, NOW);
        return reload(delivery);
    }

    @Test
    void GivenAFailedStop_WhenCorrectedToDeliveredWithNoTime_ThenItCountsFromTheOriginalAndRaisesNothing() {
        Delivery delivery = aDelivery().failed();

        correctToDelivered(delivery, Optional.empty(), new LaterAttempt.None());

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.outcomeTime()).contains(T_0800);
        assertThat(delivery.snapshot().outcomes()).hasSize(2);
        assertThat(delivery.snapshot().outcomes().get(0).outcome().kind()).isEqualTo(OutcomeKind.FAILED);
        assertThat(delivery.snapshot().outcomes().get(1).origin()).isEqualTo(new Origin.Correction(TYPO, LATER));
        assertThat(delivery.snapshot().outcomes().get(1).outcome()).isEqualTo(Outcome.Delivered.withoutProof(false));
        assertThat(delivery.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAFailedStop_WhenCorrectedToDeliveredAtAStatedTime_ThenItCountsFromThatTime() {
        Delivery delivery = aDelivery().failed();

        correctToDelivered(delivery, Optional.of(T_0900), new LaterAttempt.None());

        assertThat(delivery.outcomeTime()).contains(T_0900);
    }

    @Test
    void GivenACorrectedStop_WhenCorrectedBack_ThenTheTimeIsStillTheOriginalsAndEveryRecordStays() {
        Delivery delivery = aDelivery().failedThenCorrected();

        correctToFailed(delivery, FailureReasonKind.WRONG_ADDRESS, new LaterAttempt.None());

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.outcomeTime()).contains(T_0800);
        assertThat(delivery.snapshot().outcomes()).hasSize(3);
    }

    @Test
    void GivenAFailedStop_WhenOnlyItsReasonIsCorrected_ThenItStaysFailedWithTheNewReason() {
        Delivery delivery = aDelivery().failed();

        correctToFailed(delivery, FailureReasonKind.WRONG_ADDRESS, new LaterAttempt.None());

        assertThat(delivery.effectiveOutcome())
                .contains(new Outcome.Failed(FailureReason.of(FailureReasonKind.WRONG_ADDRESS)));
        assertThat(delivery.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAStopDeliveredInHand_WhenCorrectedToLeftAtTheDoor_ThenOnlyTheDoorFlagChanges() {
        Delivery delivery = aDelivery().delivered();

        delivery.correct(
                new DispatcherOutcome.Delivered(true),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.ALLOWED,
                LATER);

        assertThat(delivery.effectiveOutcome()).contains(Outcome.Delivered.withoutProof(true));
        assertThat(delivery.snapshot().outcomes()).hasSize(2);
        assertThat(delivery.pendingEvents()).isEmpty();
        assertThat(Delivery.restore(delivery.snapshot()).snapshot()).isEqualTo(delivery.snapshot());
    }

    @Test
    void GivenAStopLeftAtTheDoor_WhenCorrectedToHandedOver_ThenOnlyTheDoorFlagChanges() {
        Delivery delivery = aDelivery().fresh();
        delivery.recordOnBehalf(
                DISPATCHER, ROUTE_A, new DispatcherOutcome.Delivered(true), LeaveAtDoor.ALLOWED, T_0900);
        Delivery reloaded = reload(delivery);

        reloaded.correct(
                new DispatcherOutcome.Delivered(false),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.ALLOWED,
                LATER);

        assertThat(reloaded.effectiveOutcome()).contains(Outcome.Delivered.withoutProof(false));
    }

    @Test
    void GivenAFailedStopWithAnOtherReason_WhenOnlyItsTextIsCorrected_ThenTheNewTextStands() {
        Delivery delivery = failedWithOther("gate locked");

        delivery.correct(
                new DispatcherOutcome.Failed(FailureReason.other("dog in the yard")),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.NOT_ALLOWED,
                LATER);

        assertThat(delivery.effectiveOutcome()).contains(new Outcome.Failed(FailureReason.other("dog in the yard")));
        assertThat(delivery.snapshot().outcomes()).hasSize(2);
        assertThat(Delivery.restore(delivery.snapshot()).snapshot()).isEqualTo(delivery.snapshot());
    }

    @Test
    void GivenAFailedStopWithAnOtherReason_WhenCorrectedToTheSameText_ThenCorrectionChangesNothing() {
        Delivery delivery = failedWithOther("gate locked");

        assertRefusedUnchanged(
                delivery,
                () -> delivery.correct(
                        new DispatcherOutcome.Failed(FailureReason.other("gate locked")),
                        TYPO,
                        Optional.empty(),
                        DISPATCHER,
                        new LaterAttempt.None(),
                        LeaveAtDoor.NOT_ALLOWED,
                        LATER),
                new CorrectionChangesNothing(STOP, OutcomeKind.FAILED));
    }

    @Test
    void
            GivenAStopDeliveredWithAProof_WhenCorrectedToTheSameDeliveredWithNone_ThenTheProofIsNotComparedAndNothingChanges() {
        Delivery delivery = aDelivery().delivered();

        assertRefusedUnchanged(
                delivery,
                () -> correctToDelivered(delivery, Optional.empty(), new LaterAttempt.None()),
                new CorrectionChangesNothing(STOP, OutcomeKind.DELIVERED));
    }

    @Test
    void GivenAnOtherReasonCorrectedToAnotherText_WhenRetentionErasesBothTexts_ThenTheDeliveryStillRestores() {
        Delivery delivery = failedWithOther("gate locked");
        delivery.correct(
                new DispatcherOutcome.Failed(FailureReason.other("dog in the yard")),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.NOT_ALLOWED,
                LATER);
        Delivery reloaded = reload(delivery);

        reloaded.eraseExpired(Instant.parse("2027-07-01T00:00:00Z"));

        List<OutcomeRecord> outcomes = reloaded.snapshot().outcomes();
        assertThat(((Outcome.Failed) outcomes.get(0).outcome()).reason().isErased())
                .isTrue();
        assertThat(((Outcome.Failed) outcomes.get(1).outcome()).reason().isErased())
                .isTrue();
        assertThat(Delivery.restore(reloaded.snapshot()).snapshot()).isEqualTo(reloaded.snapshot());
    }

    @Test
    void GivenAPutBackStopWithNoOutcome_WhenTheFailedStopIsCorrectedToDelivered_ThenPutBackSupersededNamesItsDate() {
        Delivery delivery = aDelivery().failed();

        correctToDelivered(
                delivery, Optional.empty(), new LaterAttempt.WithoutOutcome(LATER_STOP, SECOND, PUT_BACK_DATE));

        assertThat(delivery.pendingEvents())
                .containsExactly(new PutBackSuperseded(
                        new EventId("PutBackSuperseded", STOP.value(), Version.of(1), 0),
                        LATER,
                        STOP,
                        ORDER,
                        LATER_STOP,
                        2,
                        PUT_BACK_DATE,
                        DISPATCHER));
    }

    @Test
    void GivenADeliveredStopWithALaterAttempt_WhenCorrectedToFailed_ThenNoPutBackEventFollows() {
        Delivery delivery = aDelivery().failedThenCorrected();

        correctToFailed(delivery, FailureReasonKind.DAMAGED, new LaterAttempt.WithoutOutcome(LATER_STOP, SECOND, DATE));

        assertThat(delivery.pendingEvents()).isEmpty();
    }
}
