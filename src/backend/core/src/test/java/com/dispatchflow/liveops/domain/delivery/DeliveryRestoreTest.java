package com.dispatchflow.liveops.domain.delivery;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ABSENT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.FIRST;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_2;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ORDER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.PHOTO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SIGNATURE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.TYPO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0900;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.aDelivery;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.LiveOpsFixtures;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.HeldReport;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.HeldState;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.Origin;
import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.OutcomeRecord;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link Delivery#restore} refuses each at-rest invariant broken one at a time, and takes back every
 * state the builder reaches; and the proof retention of NFR-184.
 */
class DeliveryRestoreTest {

    private static final Outcome FAILED = new Outcome.Failed(ABSENT);
    private static final Outcome DELIVERED = Outcome.Delivered.withProof(SIGNATURE, false);
    private static final OutcomeRecord REPORTED_FAILED =
            new OutcomeRecord(FAILED, DRIVER_A, T_0800, new Origin.Reported(ROUTE_A, REV_1, ONLINE, Optional.empty()));

    private static DeliverySnapshot with(List<OutcomeRecord> outcomes, List<HeldReport> held) {
        return new DeliverySnapshot(STOP, ORDER, FIRST, Version.of(3), Optional.empty(), outcomes, held);
    }

    private static HeldReport held(HeldReportId id, Outcome outcome, HeldState state) {
        return new HeldReport(id, outcome, DRIVER_A, ROUTE_A, REV_1, LATE, Optional.empty(), state);
    }

    private static void assertRestoreRefuses(DeliverySnapshot stored, String rule) {
        assertThatThrownBy(() -> Delivery.restore(stored)).isInstanceOfSatisfying(StoredStateInvalid.class, refused -> {
            assertThat(refused.subject()).isEqualTo("Delivery");
            assertThat(refused.detail()).startsWith(rule);
        });
    }

    @Test
    void GivenACorrectionWithNoOriginal_WhenRestored_ThenStoredStateInvalid() {
        assertRestoreRefuses(
                with(
                        List.of(new OutcomeRecord(FAILED, DISPATCHER, T_0800, new Origin.Correction(TYPO, NOW))),
                        List.of()),
                "the first outcome is the original");
    }

    @Test
    void GivenTwoOriginals_WhenRestored_ThenStoredStateInvalid() {
        OutcomeRecord second = new OutcomeRecord(
                DELIVERED, DRIVER_A, T_0800, new Origin.Reported(ROUTE_A, REV_1, ONLINE, Optional.empty()));
        assertRestoreRefuses(with(List.of(REPORTED_FAILED, second), List.of()), "the first outcome is the original");
    }

    @Test
    void GivenACorrectionThatChangesNothing_WhenRestored_ThenStoredStateInvalid() {
        OutcomeRecord same = new OutcomeRecord(FAILED, DISPATCHER, T_0800, new Origin.Correction(TYPO, NOW));
        assertRestoreRefuses(with(List.of(REPORTED_FAILED, same), List.of()), "a correction changes the outcome");
    }

    @Test
    void GivenAnOpenHeldReportBesideAnOutcome_WhenRestored_ThenStoredStateInvalid() {
        assertRestoreRefuses(
                with(List.of(REPORTED_FAILED), List.of(held(HELD_1, DELIVERED, new HeldState.Open()))),
                "no report stays held");
    }

    @Test
    void GivenTwoHeldReportsWithOneId_WhenRestored_ThenStoredStateInvalid() {
        HeldState refused = new HeldState.RefusedByDispatcher(DISPATCHER, NOW);
        assertRestoreRefuses(
                with(List.of(), List.of(held(HELD_1, DELIVERED, refused), held(HELD_1, FAILED, refused))),
                "each held report has its own ID");
    }

    @Test
    void GivenAConfirmedHeldReportTheOutcomeDoesNotCite_WhenRestored_ThenStoredStateInvalid() {
        assertRestoreRefuses(
                with(List.of(REPORTED_FAILED), List.of(held(HELD_1, FAILED, new HeldState.Confirmed(DISPATCHER, NOW)))),
                "a confirmed held report is exactly");
    }

    @Test
    void GivenAConfirmedOutcomeThatDiffersFromItsHeldReport_WhenRestored_ThenStoredStateInvalid() {
        OutcomeRecord confirmed = new OutcomeRecord(DELIVERED, DRIVER_A, T_0800, new Origin.ConfirmedHeld(HELD_1));
        assertRestoreRefuses(
                with(List.of(confirmed), List.of(held(HELD_1, FAILED, new HeldState.Confirmed(DISPATCHER, NOW)))),
                "a confirmed outcome is the one");
    }

    @Test
    void GivenTwoConfirmedHeldReports_WhenRestored_ThenStoredStateInvalid() {
        OutcomeRecord confirmed = new OutcomeRecord(DELIVERED, DRIVER_A, T_0800, new Origin.ConfirmedHeld(HELD_1));
        HeldState yes = new HeldState.Confirmed(DISPATCHER, NOW);
        assertRestoreRefuses(
                with(List.of(confirmed), List.of(held(HELD_1, DELIVERED, yes), held(HELD_2, DELIVERED, yes))),
                "a confirmed held report is exactly");
    }

    @Test
    void GivenAConfirmedOutcomeByAnotherAccount_WhenRestored_ThenStoredStateInvalid() {
        OutcomeRecord confirmed = new OutcomeRecord(FAILED, DISPATCHER, T_0800, new Origin.ConfirmedHeld(HELD_1));
        assertRestoreRefuses(
                with(List.of(confirmed), List.of(held(HELD_1, FAILED, new HeldState.Confirmed(DISPATCHER, NOW)))),
                "a confirmed outcome is the one");
    }

    @Test
    void GivenAConfirmedOutcomeAtAnotherTime_WhenRestored_ThenStoredStateInvalid() {
        OutcomeRecord confirmed = new OutcomeRecord(FAILED, DRIVER_A, T_0900, new Origin.ConfirmedHeld(HELD_1));
        assertRestoreRefuses(
                with(List.of(confirmed), List.of(held(HELD_1, FAILED, new HeldState.Confirmed(DISPATCHER, NOW)))),
                "a confirmed outcome is the one");
    }

    @Test
    void GivenAReportedOutcomeThatDoesNotCountFromItsReport_WhenRestored_ThenStoredStateInvalid() {
        OutcomeRecord shifted = new OutcomeRecord(
                FAILED, DRIVER_A, T_0900, new Origin.Reported(ROUTE_A, REV_1, ONLINE, Optional.empty()));
        assertRestoreRefuses(with(List.of(shifted), List.of()), "a reported outcome counts from");
    }

    @Test
    void GivenADriversDeliveredWithNoProof_WhenRestored_ThenStoredStateInvalid() {
        OutcomeRecord unproven = new OutcomeRecord(
                Outcome.Delivered.withoutProof(false),
                DRIVER_A,
                T_0800,
                new Origin.Reported(ROUTE_A, REV_1, ONLINE, Optional.empty()));
        assertRestoreRefuses(with(List.of(unproven), List.of()), "a driver's delivered has a proof");
    }

    @Test
    void GivenAHeldDoorDeliveryWithASignature_WhenRestored_ThenStoredStateInvalid() {
        assertRestoreRefuses(
                with(
                        List.of(),
                        List.of(held(HELD_1, Outcome.Delivered.withProof(SIGNATURE, true), new HeldState.Open()))),
                "a driver's delivered has a proof");
    }

    @Test
    void GivenAnOvertakenHeldReportAndNoOutcome_WhenRestored_ThenStoredStateInvalid() {
        assertRestoreRefuses(
                with(List.of(), List.of(held(HELD_1, DELIVERED, new HeldState.Overtaken(NOW)))),
                "a held report is overtaken only by an outcome");
    }

    static Stream<Supplier<Delivery>> builderStates() {
        return Stream.of(
                () -> aDelivery().fresh(),
                () -> aDelivery().arrived(),
                () -> aDelivery().delivered(),
                () -> aDelivery().failed(),
                () -> aDelivery().held(),
                () -> aDelivery().heldAfterArrival(),
                () -> aDelivery().pendingAfterRefusal(),
                () -> aDelivery().failedThenCorrected());
    }

    @ParameterizedTest
    @MethodSource("builderStates")
    void GivenEveryBuilderState_WhenRestoredFromItsSnapshot_ThenTheSnapshotRoundTrips(Supplier<Delivery> state) {
        Delivery delivery = state.get();

        assertThat(Delivery.restore(delivery.snapshot()).snapshot()).isEqualTo(delivery.snapshot());
    }

    @Test
    void GivenProofsReceivedExactlyNinetyDaysAgoInIsraelTime_WhenErased_ThenFileAndNoteGoAndTheKindStays() {
        Delivery delivery = aDelivery().fresh();
        delivery.deliver(
                byA(ONLINE),
                Optional.of(PHOTO),
                true,
                LeaveAtDoor.ALLOWED,
                new Entitlement.OnRoute(REV_1, DRIVER_A),
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
                new Entitlement.OnRoute(REV_1, DRIVER_A),
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
                new Entitlement.OnRoute(REV_1, DRIVER_A),
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

        List<OutcomeRecord> outcomes = delivery.snapshot().outcomes();
        FailureReason original = ((Outcome.Failed) outcomes.get(0).outcome()).reason();
        assertThat(original.kind()).isEqualTo(FailureReasonKind.OTHER);
        assertThat(original.isErased()).isTrue();
        assertThat(outcomes.get(1).origin()).isEqualTo(new Origin.Correction(TYPO.erased(), NOW));
        assertThat(Delivery.restore(delivery.snapshot()).snapshot()).isEqualTo(delivery.snapshot());
    }

    @Test
    void GivenReasonsOneSecondShortOfNinetyDays_WhenErased_ThenTheirTextsStay() {
        Delivery delivery = aDelivery().fresh();
        delivery.fail(
                byA(ONLINE),
                FailureReason.other("gate locked"),
                new Entitlement.OnRoute(REV_1, DRIVER_A),
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
                new Entitlement.OnRoute(REV_1, DRIVER_A),
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

        List<OutcomeRecord> outcomes = delivery.snapshot().outcomes();
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
        assertThat(delivery.snapshot().heldReports().get(0).outcome())
                .isEqualTo(new Outcome.Delivered(Optional.of(erased), false));
    }
}
