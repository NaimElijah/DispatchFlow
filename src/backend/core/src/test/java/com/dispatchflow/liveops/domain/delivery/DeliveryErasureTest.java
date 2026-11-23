package com.dispatchflow.liveops.domain.delivery;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.PHOTO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.TYPO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.aDelivery;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.liveops.domain.LiveOpsFixtures;
import com.dispatchflow.liveops.domain.delivery.history.Origin;
import com.dispatchflow.liveops.domain.delivery.history.OutcomeRecord;
import com.dispatchflow.liveops.domain.outcome.DispatcherOutcome;
import com.dispatchflow.liveops.domain.outcome.FailureReason;
import com.dispatchflow.liveops.domain.outcome.FailureReasonKind;
import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.liveops.domain.proof.Proof;
import com.dispatchflow.liveops.domain.proof.ProofKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link Delivery#eraseExpired} keeps every record and erases the personal data whose retention has
 * ended, 90 Israel days after it reached the server (NFR-31, NFR-184, ADR-107): a proof's file and
 * note, an <em>other</em> reason's text and a correction reason's text.
 */
class DeliveryErasureTest {

    @Test
    void GivenProofsReceivedExactlyNinetyDaysAgoInIsraelTime_WhenErased_ThenFileAndNoteGoAndTheKindStays() {
        Delivery delivery = aDelivery().fresh();
        delivery.deliver(
                byA(ONLINE),
                Optional.of(PHOTO),
                true,
                LeaveAtDoor.ALLOWED,
                new Entitlement.OnRoute(REV_1, DRIVER_A, DRIVER_A),
                LiveOpsFixtures.NO_HOLD,
                HELD_1,
                NOW);
        Instant ninetyDaysOn = Instant.parse("2027-05-30T07:00:00Z"); // 10:00 local after the spring change

        delivery.eraseExpired(ninetyDaysOn);

        Outcome.Delivered outcome =
                (Outcome.Delivered) delivery.effectiveOutcome().orElseThrow();
        assertThat(outcome.proof()).contains(new Proof(ProofKind.PHOTO, Optional.empty(), Optional.empty()));
        assertThat(delivery.pendingEvents()).hasSize(1);
    }

    @Test
    void GivenProofsReceivedOneSecondShortOfNinetyDays_WhenErased_ThenTheyStay() {
        Delivery delivery = aDelivery().fresh();
        delivery.deliver(
                byA(ONLINE),
                Optional.of(PHOTO),
                true,
                LeaveAtDoor.ALLOWED,
                new Entitlement.OnRoute(REV_1, DRIVER_A, DRIVER_A),
                LiveOpsFixtures.NO_HOLD,
                HELD_1,
                NOW);

        delivery.eraseExpired(Instant.parse("2027-05-30T06:59:59Z"));

        assertThat(delivery.effectiveOutcome()).contains(Outcome.Delivered.withProof(PHOTO, true));
    }

    @Test
    void GivenAnOtherReasonAndACorrectionReasonNinetyDaysOld_WhenErased_ThenTheirTextsGoAndTheKindsStay() {
        Delivery delivery = aDelivery().fresh();
        delivery.fail(
                byA(ONLINE),
                FailureReason.other("gate locked"),
                new Entitlement.OnRoute(REV_1, DRIVER_A, DRIVER_A),
                LiveOpsFixtures.NO_HOLD,
                HELD_1,
                NOW);
        delivery.correct(
                new DispatcherOutcome.Failed(FailureReason.of(FailureReasonKind.NO_ACCESS)),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.NOT_ALLOWED,
                NOW);

        // 90 days after the correction at 12:00 local, which is 12:00 local after the spring change.
        delivery.eraseExpired(Instant.parse("2027-05-30T09:00:00Z"));

        List<OutcomeRecord> outcomes = delivery.outcomes();
        FailureReason original = ((Outcome.Failed) outcomes.get(0).outcome()).reason();
        assertThat(original.kind()).isEqualTo(FailureReasonKind.OTHER);
        assertThat(original.isErased()).isTrue();
        assertThat(outcomes.get(1).origin()).isEqualTo(new Origin.Correction(TYPO.erased(), NOW));
    }

    @Test
    void GivenReasonsOneSecondShortOfNinetyDays_WhenErased_ThenTheirTextsStay() {
        Delivery delivery = aDelivery().fresh();
        delivery.fail(
                byA(ONLINE),
                FailureReason.other("gate locked"),
                new Entitlement.OnRoute(REV_1, DRIVER_A, DRIVER_A),
                LiveOpsFixtures.NO_HOLD,
                HELD_1,
                NOW);

        delivery.eraseExpired(Instant.parse("2027-05-30T06:59:59Z"));

        assertThat(delivery.effectiveOutcome()).contains(new Outcome.Failed(FailureReason.other("gate locked")));
    }

    @Test
    void GivenACorrectionReasonOneSecondShortOfNinetyDays_WhenErased_ThenItsTextStaysWhileTheOriginalsGoes() {
        Delivery delivery = aDelivery().fresh();
        delivery.fail(
                byA(ONLINE),
                FailureReason.other("gate locked"),
                new Entitlement.OnRoute(REV_1, DRIVER_A, DRIVER_A),
                LiveOpsFixtures.NO_HOLD,
                HELD_1,
                NOW);
        delivery.correct(
                new DispatcherOutcome.Failed(FailureReason.of(FailureReasonKind.NO_ACCESS)),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.NOT_ALLOWED,
                NOW);

        // The correction at 12:00 local is due at 12:00 local on 30 May, 09:00Z after the spring change.
        delivery.eraseExpired(Instant.parse("2027-05-30T08:59:59Z"));

        List<OutcomeRecord> outcomes = delivery.outcomes();
        assertThat(((Outcome.Failed) outcomes.get(0).outcome()).reason().isErased())
                .isTrue();
        assertThat(outcomes.get(1).origin()).isEqualTo(new Origin.Correction(TYPO, NOW));
    }

    @Test
    void GivenAConfirmedHeldReportsProof_WhenErased_ThenBothCopiesGo() {
        Delivery delivery = aDelivery().held();
        delivery.confirmHeld(HELD_1, DISPATCHER, LATER);

        delivery.eraseExpired(Instant.parse("2027-05-30T10:00:00Z"));

        Proof erased = new Proof(ProofKind.SIGNATURE, Optional.empty(), Optional.empty());
        assertThat(delivery.effectiveOutcome()).contains(new Outcome.Delivered(Optional.of(erased), false));
        assertThat(delivery.heldReports().get(0).outcome())
                .isEqualTo(new Outcome.Delivered(Optional.of(erased), false));
    }
}
