package com.dispatchflow.liveops.domain;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ABSENT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_C;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_2;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_3;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.MOVED_WITH_CONTACT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NO_HOLD;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_ROUTE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ORDER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.PHOTO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.REV_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_C;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SERVING;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SIGNATURE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.aDelivery;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byC;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.reload;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.liveops.domain.DeliverySnapshot.HeldState;
import com.dispatchflow.liveops.domain.DeliverySnapshot.Origin;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.liveops.events.OutcomeRecorded;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * How a driver's reports and a dispatcher's records become a stop's facts (DL §8, §9.3): the event
 * an outcome raises, the hold of BR-132 at its window's edges, BR-136's further hold, an outcome on
 * the current route overtaking held reports (BR-134), confirming and refusing (BR-135), and
 * *served after cancellation* (BR-130).
 */
class DeliveryReportsTest {

    private static final HoldCheck NOW_ON_A_NO_CONTACT =
            HoldCheck.of(new StopWhereabouts.OnAnotherRoute(ROUTE_A), List.of());

    @Test
    void GivenANewStop_WhenADriverDelivers_ThenOutcomeRecordedNamesTheStopRouteAndCorrectedTime() {
        Delivery delivery = aDelivery().fresh();

        ReportAnswer answer = delivery.deliver(
                byA(ONLINE), Optional.of(SIGNATURE), false, LeaveAtDoor.NOT_ALLOWED, ON_ROUTE, NO_HOLD, HELD_1, NOW);

        assertThat(answer).isEqualTo(new ReportAnswer.Recorded(List.of()));
        assertThat(delivery.pendingEvents())
                .containsExactly(new OutcomeRecorded(
                        new EventId("OutcomeRecorded", STOP.value(), Version.of(1), 0),
                        NOW,
                        STOP,
                        ORDER,
                        1,
                        ROUTE_A,
                        OutcomeKind.DELIVERED,
                        T_0800,
                        false,
                        DRIVER_A));
        assertThat(delivery.outcomeTime()).contains(T_0800);
        assertThat(delivery.effectiveOutcome()).contains(Outcome.Delivered.withProof(SIGNATURE, false));
    }

    @Test
    void GivenALeaveAtDoorOrder_WhenADriverLeavesItWithAPhoto_ThenItIsRecordedAsLeftAtTheDoor() {
        Delivery delivery = aDelivery().fresh();

        delivery.deliver(byA(ONLINE), Optional.of(PHOTO), true, LeaveAtDoor.ALLOWED, ON_ROUTE, NO_HOLD, HELD_1, NOW);

        assertThat(delivery.effectiveOutcome()).contains(Outcome.Delivered.withProof(PHOTO, true));
    }

    @Test
    void GivenANewStop_WhenADispatcherRecordsOnBehalf_ThenItNeedsNoProofAndCountsFromNow() {
        Delivery delivery = aDelivery().fresh();

        delivery.recordOnBehalf(DISPATCHER, ROUTE_A, new DispatcherOutcome.Delivered(true), LeaveAtDoor.ALLOWED, NOW);

        assertThat(delivery.pendingEvents())
                .containsExactly(new OutcomeRecorded(
                        new EventId("OutcomeRecorded", STOP.value(), Version.of(1), 0),
                        NOW,
                        STOP,
                        ORDER,
                        1,
                        ROUTE_A,
                        OutcomeKind.DELIVERED,
                        NOW,
                        true,
                        DISPATCHER));
        assertThat(delivery.snapshot().outcomes().get(0).origin()).isEqualTo(new Origin.OnBehalf(ROUTE_A));
    }

    @Test
    void GivenALateReportForAStopNowOnAnotherRoute_WhenTheAccountWasInContact_ThenItIsHeldAndRaisesNothing() {
        Delivery delivery = aDelivery().fresh();

        ReportAnswer answer = delivery.deliver(
                byA(LATE),
                Optional.of(SIGNATURE),
                false,
                LeaveAtDoor.NOT_ALLOWED,
                ON_ROUTE,
                MOVED_WITH_CONTACT,
                HELD_1,
                NOW);

        assertThat(answer).isEqualTo(new ReportAnswer.Held(HELD_1));
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.HELD);
        assertThat(delivery.effectiveOutcome()).isEmpty();
        assertThat(delivery.pendingEvents()).isEmpty();
    }

    @ParameterizedTest(name = "contact at {0}: held {1}")
    @CsvSource({
        "2027-03-01T08:01:59Z, false",
        "2027-03-01T08:02:00Z, true",
        "2027-03-01T08:02:01Z, true",
        "2027-03-01T09:57:59Z, true",
        "2027-03-01T09:58:00Z, true",
        "2027-03-01T09:58:01Z, false"
    })
    void GivenAContactNearTheWindowsEdges_WhenALateReportArrives_ThenBothEndsCount(String contact, boolean held) {
        Delivery delivery = aDelivery().fresh();
        HoldCheck check = HoldCheck.of(ON_B, List.of(Instant.parse(contact)));

        ReportAnswer answer = delivery.fail(byA(LATE), ABSENT, ON_ROUTE, check, HELD_1, NOW);

        assertThat(answer instanceof ReportAnswer.Held).isEqualTo(held);
    }

    @Test
    void GivenALateReportBeforeACancellation_WhenNotHeld_ThenItIsServedAfterCancellation() {
        Delivery delivery = aDelivery().fresh();
        StopWhereabouts cancelled = new StopWhereabouts.Cancelled(
                CancellationKind.ORDER_RESCHEDULED, Instant.parse("2027-03-01T09:00:00Z"));

        delivery.fail(byA(LATE), ABSENT, ON_ROUTE, HoldCheck.of(cancelled, List.of()), HELD_1, NOW);

        assertThat(delivery.snapshot().outcomes().get(0).origin())
                .isEqualTo(new Origin.Reported(ROUTE_A, REV_1, LATE, Optional.of(CancellationKind.ORDER_RESCHEDULED)));
    }

    @Test
    void GivenALateReportBeforeACancellation_WhenTheAccountWasInContact_ThenItIsHeldAsOverridingIt() {
        Delivery delivery = aDelivery().fresh();
        StopWhereabouts cancelled =
                new StopWhereabouts.Cancelled(CancellationKind.PLAN_CANCELLED, Instant.parse("2027-03-01T09:30:00Z"));

        ReportAnswer answer = delivery.fail(
                byA(LATE),
                ABSENT,
                ON_ROUTE,
                HoldCheck.of(cancelled, List.of(Instant.parse("2027-03-01T09:00:00Z"))),
                HELD_1,
                NOW);

        assertThat(answer).isEqualTo(new ReportAnswer.Held(HELD_1));
        assertThat(delivery.snapshot().heldReports().get(0).overrides()).contains(CancellationKind.PLAN_CANCELLED);
    }

    @Test
    void GivenADroppedStop_WhenALateReportArrivesWithContact_ThenItIsRecordedNotHeld() {
        Delivery delivery = aDelivery().fresh();

        ReportAnswer answer = delivery.fail(
                byA(LATE),
                ABSENT,
                ON_ROUTE,
                HoldCheck.of(new StopWhereabouts.Dropped(), List.of(Instant.parse("2027-03-01T09:00:00Z"))),
                HELD_1,
                NOW);

        assertThat(answer).isEqualTo(new ReportAnswer.Recorded(List.of()));
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void GivenAnotherDriversHeldReport_WhenAFurtherLateReportArrivesWithNoContact_ThenItIsHeldToo() {
        Delivery delivery = aDelivery().held();

        ReportAnswer answer = delivery.fail(byC(LATE), ABSENT, ON_ROUTE, NOW_ON_A_NO_CONTACT, HELD_2, NOW);

        assertThat(answer).isEqualTo(new ReportAnswer.Held(HELD_2));
        assertThat(delivery.snapshot().heldReports())
                .extracting(DeliverySnapshot.HeldReport::id)
                .containsExactly(HELD_1, HELD_2);
    }

    @Test
    void GivenTwoHeldReports_WhenTheStopsCurrentDriverDelivers_ThenBothAreOvertakenAndTheOutcomeStands() {
        Delivery delivery = aDelivery().held();
        DeliveryReportsTest.holdAnother(delivery);
        Delivery reloaded = reload(delivery);

        ReportAnswer answer = reloaded.deliver(
                byA(ONLINE), Optional.of(SIGNATURE), false, LeaveAtDoor.NOT_ALLOWED, ON_ROUTE, NO_HOLD, HELD_3, LATER);

        assertThat(answer).isEqualTo(new ReportAnswer.Recorded(List.of(HELD_1, HELD_2)));
        assertThat(reloaded.snapshot().heldReports())
                .extracting(DeliverySnapshot.HeldReport::state)
                .containsExactly(new HeldState.Overtaken(LATER), new HeldState.Overtaken(LATER));
        assertThat(reloaded.status()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void GivenTwoHeldReports_WhenOneIsConfirmed_ThenItIsTheOutcomeAndTheOtherIsOvertaken() {
        Delivery delivery = aDelivery().held();
        DeliveryReportsTest.holdAnother(delivery);
        Delivery reloaded = reload(delivery);

        ReportAnswer answer = reloaded.confirmHeld(HELD_2, DISPATCHER, LATER);

        assertThat(answer).isEqualTo(new ReportAnswer.Recorded(List.of(HELD_1)));
        assertThat(reloaded.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(reloaded.outcomeTime()).contains(T_0800);
        assertThat(reloaded.snapshot().heldReports())
                .extracting(DeliverySnapshot.HeldReport::state)
                .containsExactly(new HeldState.Overtaken(LATER), new HeldState.Confirmed(DISPATCHER, LATER));
        assertThat(reloaded.pendingEvents())
                .containsExactly(new OutcomeRecorded(
                        new EventId("OutcomeRecorded", STOP.value(), Version.of(3), 0),
                        LATER,
                        STOP,
                        ORDER,
                        1,
                        ROUTE_C,
                        OutcomeKind.FAILED,
                        T_0800,
                        false,
                        DRIVER_C));
    }

    @Test
    void GivenTwoHeldReports_WhenOneIsRefused_ThenTheStopStaysHeld() {
        Delivery delivery = aDelivery().held();
        DeliveryReportsTest.holdAnother(delivery);
        Delivery reloaded = reload(delivery);

        reloaded.refuseHeld(HELD_1, DISPATCHER, LATER);

        assertThat(reloaded.status()).isEqualTo(DeliveryStatus.HELD);
        assertThat(reloaded.snapshot().heldReports().get(0).state())
                .isEqualTo(new HeldState.RefusedByDispatcher(DISPATCHER, LATER));
        assertThat(reloaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAHeldReport_WhenTheCurrentRoutesDriverArrives_ThenTheArrivalIsKeptAndTheStopStaysHeld() {
        Delivery delivery = aDelivery().held();

        delivery.arrive(byA(ONLINE), ON_ROUTE, SERVING);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.HELD);
        assertThat(delivery.arrivedAt()).contains(T_0800);
        assertThat(delivery.snapshot().arrival().orElseThrow().driver()).isEqualTo(DRIVER_A);
    }

    @Test
    void GivenAHeldReport_WhenADispatcherRecordsOnBehalf_ThenTheHeldReportIsOvertaken() {
        Delivery delivery = aDelivery().held();

        ReportAnswer answer = delivery.recordOnBehalf(
                DISPATCHER, ROUTE_A, new DispatcherOutcome.Failed(ABSENT), LeaveAtDoor.NOT_ALLOWED, LATER);

        assertThat(answer).isEqualTo(new ReportAnswer.Recorded(List.of(HELD_1)));
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void GivenADeliveryWithEveryKindOfRecord_WhenPrinted_ThenNoReasonOrNoteAppears() {
        Delivery delivery = aDelivery().fresh();
        delivery.fail(byA(ONLINE), FailureReason.other("neighbour said moved away"), ON_ROUTE, NO_HOLD, HELD_1, NOW);

        assertThat(delivery.toString()).doesNotContain("neighbour");
        assertThat(delivery.snapshot().toString()).doesNotContain("neighbour");
        assertThat(delivery.snapshot().outcomes().toString()).doesNotContain("neighbour");
    }

    /** Driver C's late *failed*, held as HELD_2 beside B's because a report is already held (BR-136). */
    private static void holdAnother(Delivery delivery) {
        delivery.fail(byC(LATE), ABSENT, ON_ROUTE, NOW_ON_A_NO_CONTACT, HELD_2, NOW);
    }

    @Test
    void GivenTheProofPhotosNote_WhenPrinted_ThenItIsHidden() {
        assertThat(PHOTO.toString()).doesNotContain("blue door");
    }
}
