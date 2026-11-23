package com.dispatchflow.liveops.domain.delivery;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ABSENT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_2;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER_STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.MOVED_TO_A_WITH_CONTACT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOT_ON_ROUTE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NO_HOLD;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_ROUTE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_ROUTE_B;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.PHOTO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SECOND;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SERVING;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SIGNATURE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.TYPO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.aDelivery;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.assertRefusedUnchanged;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byB;
import static com.dispatchflow.shared.AsSaved.asSaved;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.LiveOpsError.ArrivalAfterOutcome;
import com.dispatchflow.liveops.domain.LiveOpsError.ArrivalAlreadyRecorded;
import com.dispatchflow.liveops.domain.LiveOpsError.CorrectionChangesNothing;
import com.dispatchflow.liveops.domain.LiveOpsError.CorrectionTimeInFuture;
import com.dispatchflow.liveops.domain.LiveOpsError.LaterAttemptHasOutcome;
import com.dispatchflow.liveops.domain.LiveOpsError.LeftAtDoorNeedsPhoto;
import com.dispatchflow.liveops.domain.LiveOpsError.NoOutcomeToCorrect;
import com.dispatchflow.liveops.domain.LiveOpsError.NotALeaveAtDoorOrder;
import com.dispatchflow.liveops.domain.LiveOpsError.OutcomeAlreadyRecorded;
import com.dispatchflow.liveops.domain.LiveOpsError.ProofHasNoFile;
import com.dispatchflow.liveops.domain.LiveOpsError.ProofRequired;
import com.dispatchflow.liveops.domain.LiveOpsError.ReportAfterCancellation;
import com.dispatchflow.liveops.domain.LiveOpsError.ReportNotHeld;
import com.dispatchflow.liveops.domain.LiveOpsError.SameOutcomeAlreadyRecorded;
import com.dispatchflow.liveops.domain.LiveOpsError.StopNotOnYourRoute;
import com.dispatchflow.liveops.domain.LiveOpsFixtures;
import com.dispatchflow.liveops.domain.outcome.DispatcherOutcome;
import com.dispatchflow.liveops.domain.outcome.FailureReason;
import com.dispatchflow.liveops.domain.outcome.FailureReasonKind;
import com.dispatchflow.liveops.domain.proof.Proof;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.shared.DomainError;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every refusal each {@link Delivery} command can make, in each command's order (DL §4.5), with the
 * state and the events unchanged; and the null checks that come before any rule.
 */
class DeliveryRefusalsTest {

    private static final StopWhereabouts CANCELLED_AT_0800 =
            new StopWhereabouts.Cancelled(CancellationKind.ORDER_CANCELLED, T_0800);
    /** Route A held the stop at the report's time, but B drove it then: A had handed it over. */
    private static final Entitlement HANDED_TO_B = new Entitlement.OnRoute(LiveOpsFixtures.REV_2, DRIVER_B, DRIVER_B);

    /** Route A held the stop at the report's time with A driving it, but it is B's route now. */
    private static final Entitlement HANDED_FROM_A_TO_B =
            new Entitlement.OnRoute(LiveOpsFixtures.REV_1, LiveOpsFixtures.DRIVER_A, DRIVER_B);

    private static final StopWhereabouts RESCHEDULED_AT_0759 =
            new StopWhereabouts.Cancelled(CancellationKind.ORDER_RESCHEDULED, Instant.parse("2027-03-01T07:59:59Z"));

    private static void deliverOnline(Delivery delivery, Optional<Proof> proof, boolean door, LeaveAtDoor allowed) {
        delivery.deliver(byA(ONLINE), proof, door, allowed, ON_ROUTE, NO_HOLD, HELD_2, LATER);
    }

    private static void correctToDelivered(Delivery delivery, LaterAttempt later) {
        delivery.correct(
                new DispatcherOutcome.Delivered(false),
                TYPO,
                Optional.empty(),
                DISPATCHER,
                later,
                LeaveAtDoor.NOT_ALLOWED,
                LATER);
    }

    private static Delivery failedWithOther() {
        Delivery delivery = aDelivery().fresh();
        delivery.fail(byA(ONLINE), FailureReason.other("gate locked"), ON_ROUTE, NO_HOLD, HELD_1, LATER);
        return asSaved(delivery);
    }

    private static void correctToDeliveredAt(Delivery delivery, Instant stated) {
        delivery.correct(
                new DispatcherOutcome.Delivered(false),
                TYPO,
                Optional.of(stated),
                DISPATCHER,
                new LaterAttempt.None(),
                LeaveAtDoor.NOT_ALLOWED,
                LATER);
    }

    static Stream<Arguments> refusals() {
        Supplier<Delivery> fresh = () -> aDelivery().fresh();
        Supplier<Delivery> delivered = () -> aDelivery().delivered();
        Supplier<Delivery> failed = () -> aDelivery().failed();
        Supplier<Delivery> arrived = () -> aDelivery().arrived();
        Supplier<Delivery> held = () -> aDelivery().held();
        Supplier<Delivery> failedOther = DeliveryRefusalsTest::failedWithOther;
        return Stream.of(
                arguments(
                        "deliver, cancelled and not entitled: the cancellation comes first",
                        fresh,
                        (Consumer<Delivery>) d -> d.deliver(
                                byA(ONLINE),
                                Optional.of(SIGNATURE),
                                false,
                                LeaveAtDoor.NOT_ALLOWED,
                                NOT_ON_ROUTE,
                                HoldCheck.of(CANCELLED_AT_0800, List.of()),
                                HELD_2,
                                LATER),
                        new ReportAfterCancellation(STOP, CancellationKind.ORDER_CANCELLED)),
                arguments(
                        "fail, cancelled and not entitled: the cancellation comes first",
                        fresh,
                        (Consumer<Delivery>) d -> d.fail(
                                byA(ONLINE),
                                ABSENT,
                                NOT_ON_ROUTE,
                                HoldCheck.of(CANCELLED_AT_0800, List.of()),
                                HELD_2,
                                LATER),
                        new ReportAfterCancellation(STOP, CancellationKind.ORDER_CANCELLED)),
                arguments(
                        "deliver, not entitled and an outcome stands: entitlement comes first",
                        failed,
                        (Consumer<Delivery>) d -> d.deliver(
                                byA(ONLINE),
                                Optional.of(SIGNATURE),
                                false,
                                LeaveAtDoor.NOT_ALLOWED,
                                NOT_ON_ROUTE,
                                NO_HOLD,
                                HELD_2,
                                LATER),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "fail, not entitled and an outcome stands: entitlement comes first",
                        delivered,
                        (Consumer<Delivery>) d -> d.fail(byA(ONLINE), ABSENT, NOT_ON_ROUTE, NO_HOLD, HELD_2, LATER),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "on behalf, at the door of an order that may not be, with an outcome standing: the door comes first",
                        delivered,
                        (Consumer<Delivery>) d -> d.recordOnBehalf(
                                DISPATCHER,
                                ROUTE_A,
                                new DispatcherOutcome.Delivered(true),
                                LeaveAtDoor.NOT_ALLOWED,
                                LATER),
                        new NotALeaveAtDoorOrder(STOP)),
                arguments(
                        "fail, other with another text: the text is not compared",
                        failedOther,
                        (Consumer<Delivery>) d -> d.fail(
                                byA(ONLINE), FailureReason.other("dog in the yard"), ON_ROUTE, NO_HOLD, HELD_2, LATER),
                        new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.FAILED)),
                arguments(
                        "correct, other to other with the same text: nothing changes",
                        failedOther,
                        (Consumer<Delivery>) d -> d.correct(
                                new DispatcherOutcome.Failed(FailureReason.other("gate locked")),
                                TYPO,
                                Optional.empty(),
                                DISPATCHER,
                                new LaterAttempt.None(),
                                LeaveAtDoor.NOT_ALLOWED,
                                LATER),
                        new CorrectionChangesNothing(STOP, OutcomeKind.FAILED)),
                arguments(
                        "deliver, a proof whose file was erased",
                        fresh,
                        (Consumer<Delivery>)
                                d -> deliverOnline(d, Optional.of(SIGNATURE.erased()), false, LeaveAtDoor.NOT_ALLOWED),
                        new ProofHasNoFile(STOP)),
                arguments(
                        "correct, a stated time after now",
                        failed,
                        (Consumer<Delivery>) d -> correctToDeliveredAt(d, LATER.plusSeconds(1)),
                        new CorrectionTimeInFuture(STOP)),
                arguments(
                        "deliver, repeating the same driver's held report",
                        held,
                        (Consumer<Delivery>) d -> d.deliver(
                                byB(LATE),
                                Optional.of(SIGNATURE),
                                false,
                                LeaveAtDoor.NOT_ALLOWED,
                                ON_ROUTE_B,
                                MOVED_TO_A_WITH_CONTACT,
                                HELD_2,
                                LATER),
                        new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED)),
                arguments(
                        "fail, contradicting the same driver's held report",
                        held,
                        (Consumer<Delivery>)
                                d -> d.fail(byB(LATE), ABSENT, ON_ROUTE_B, MOVED_TO_A_WITH_CONTACT, HELD_2, LATER),
                        new OutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED)),
                arguments(
                        "arrive, timed at a cancellation",
                        fresh,
                        (Consumer<Delivery>) d -> d.arrive(byA(ONLINE), ON_ROUTE, CANCELLED_AT_0800),
                        new ReportAfterCancellation(STOP, CancellationKind.ORDER_CANCELLED)),
                arguments(
                        "arrive, cancelled and not entitled: the cancellation comes first",
                        fresh,
                        (Consumer<Delivery>) d -> d.arrive(byA(ONLINE), NOT_ON_ROUTE, CANCELLED_AT_0800),
                        new ReportAfterCancellation(STOP, CancellationKind.ORDER_CANCELLED)),
                arguments(
                        "arrive, not entitled",
                        fresh,
                        (Consumer<Delivery>) d -> d.arrive(byA(ONLINE), NOT_ON_ROUTE, SERVING),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "arrive, not entitled and an outcome stands: entitlement comes first",
                        delivered,
                        (Consumer<Delivery>) d -> d.arrive(byA(ONLINE), NOT_ON_ROUTE, SERVING),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "arrive, from the driver the route was handed away from",
                        fresh,
                        (Consumer<Delivery>) d -> d.arrive(byA(ONLINE), HANDED_TO_B, SERVING),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "deliver, from the driver the route was handed away from",
                        fresh,
                        (Consumer<Delivery>) d -> d.deliver(
                                byA(ONLINE),
                                Optional.of(SIGNATURE),
                                false,
                                LeaveAtDoor.NOT_ALLOWED,
                                HANDED_TO_B,
                                NO_HOLD,
                                HELD_2,
                                LATER),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "fail, from the driver the route was handed away from",
                        fresh,
                        (Consumer<Delivery>) d -> d.fail(byA(ONLINE), ABSENT, HANDED_TO_B, NO_HOLD, HELD_2, LATER),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "fail, from the driver the route was handed away from, and an outcome stands: entitlement"
                                + " comes first",
                        delivered,
                        (Consumer<Delivery>) d -> d.fail(byA(ONLINE), ABSENT, HANDED_TO_B, NO_HOLD, HELD_2, LATER),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "fail, from the driver the route was handed away from, timed at a cancellation: the"
                                + " cancellation comes first",
                        fresh,
                        (Consumer<Delivery>) d -> d.fail(
                                byA(ONLINE),
                                ABSENT,
                                HANDED_TO_B,
                                HoldCheck.of(CANCELLED_AT_0800, List.of()),
                                HELD_2,
                                LATER),
                        new ReportAfterCancellation(STOP, CancellationKind.ORDER_CANCELLED)),
                arguments(
                        "arrive, from the driver the route was handed away from since, timed before the hand-over",
                        fresh,
                        (Consumer<Delivery>) d -> d.arrive(byA(LATE), HANDED_FROM_A_TO_B, SERVING),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "arrive, after an outcome",
                        delivered,
                        (Consumer<Delivery>) d -> d.arrive(byA(ONLINE), ON_ROUTE, SERVING),
                        new ArrivalAfterOutcome(STOP)),
                arguments(
                        "arrive, twice",
                        arrived,
                        (Consumer<Delivery>) d -> d.arrive(byA(ONLINE), ON_ROUTE, SERVING),
                        new ArrivalAlreadyRecorded(STOP)),
                arguments(
                        "deliver, no proof",
                        fresh,
                        (Consumer<Delivery>) d -> deliverOnline(d, Optional.empty(), false, LeaveAtDoor.ALLOWED),
                        new ProofRequired(STOP)),
                arguments(
                        "deliver, no proof and not a door order: the proof comes first",
                        fresh,
                        (Consumer<Delivery>) d -> deliverOnline(d, Optional.empty(), true, LeaveAtDoor.NOT_ALLOWED),
                        new ProofRequired(STOP)),
                arguments(
                        "deliver, left at the door of an order that may not be",
                        fresh,
                        (Consumer<Delivery>)
                                d -> deliverOnline(d, Optional.of(SIGNATURE), true, LeaveAtDoor.NOT_ALLOWED),
                        new NotALeaveAtDoorOrder(STOP)),
                arguments(
                        "deliver, left at the door with a signature",
                        fresh,
                        (Consumer<Delivery>) d -> deliverOnline(d, Optional.of(SIGNATURE), true, LeaveAtDoor.ALLOWED),
                        new LeftAtDoorNeedsPhoto(STOP)),
                arguments(
                        "deliver, left at the door with a signature and already delivered: content comes first",
                        delivered,
                        (Consumer<Delivery>) d -> deliverOnline(d, Optional.of(SIGNATURE), true, LeaveAtDoor.ALLOWED),
                        new LeftAtDoorNeedsPhoto(STOP)),
                arguments(
                        "deliver, timed after a reschedule",
                        fresh,
                        (Consumer<Delivery>) d -> d.deliver(
                                byA(ONLINE),
                                Optional.of(SIGNATURE),
                                false,
                                LeaveAtDoor.NOT_ALLOWED,
                                ON_ROUTE,
                                HoldCheck.of(RESCHEDULED_AT_0759, List.of()),
                                HELD_2,
                                LATER),
                        new ReportAfterCancellation(STOP, CancellationKind.ORDER_RESCHEDULED)),
                arguments(
                        "deliver, not entitled",
                        fresh,
                        (Consumer<Delivery>) d -> d.deliver(
                                byA(ONLINE),
                                Optional.of(SIGNATURE),
                                false,
                                LeaveAtDoor.NOT_ALLOWED,
                                NOT_ON_ROUTE,
                                NO_HOLD,
                                HELD_2,
                                LATER),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "deliver, repeating delivered",
                        delivered,
                        (Consumer<Delivery>) d -> deliverOnline(d, Optional.of(PHOTO), true, LeaveAtDoor.ALLOWED),
                        new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED)),
                arguments(
                        "deliver, contradicting failed",
                        failed,
                        (Consumer<Delivery>)
                                d -> deliverOnline(d, Optional.of(SIGNATURE), false, LeaveAtDoor.NOT_ALLOWED),
                        new OutcomeAlreadyRecorded(STOP, OutcomeKind.FAILED)),
                arguments(
                        "fail, timed at a cancellation",
                        fresh,
                        (Consumer<Delivery>) d -> d.fail(
                                byA(ONLINE),
                                ABSENT,
                                ON_ROUTE,
                                HoldCheck.of(CANCELLED_AT_0800, List.of()),
                                HELD_2,
                                LATER),
                        new ReportAfterCancellation(STOP, CancellationKind.ORDER_CANCELLED)),
                arguments(
                        "fail, not entitled",
                        fresh,
                        (Consumer<Delivery>) d -> d.fail(byA(ONLINE), ABSENT, NOT_ON_ROUTE, NO_HOLD, HELD_2, LATER),
                        new StopNotOnYourRoute(STOP, ROUTE_A)),
                arguments(
                        "fail, repeating the same reason",
                        failed,
                        (Consumer<Delivery>) d -> d.fail(byA(ONLINE), ABSENT, ON_ROUTE, NO_HOLD, HELD_2, LATER),
                        new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.FAILED)),
                arguments(
                        "fail, with another reason",
                        failed,
                        (Consumer<Delivery>) d -> d.fail(
                                byA(ONLINE),
                                FailureReason.of(FailureReasonKind.NO_ACCESS),
                                ON_ROUTE,
                                NO_HOLD,
                                HELD_2,
                                LATER),
                        new OutcomeAlreadyRecorded(STOP, OutcomeKind.FAILED)),
                arguments(
                        "fail, contradicting delivered",
                        delivered,
                        (Consumer<Delivery>) d -> d.fail(byA(ONLINE), ABSENT, ON_ROUTE, NO_HOLD, HELD_2, LATER),
                        new OutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED)),
                arguments(
                        "on behalf, left at the door of an order that may not be",
                        fresh,
                        (Consumer<Delivery>) d -> d.recordOnBehalf(
                                DISPATCHER,
                                ROUTE_A,
                                new DispatcherOutcome.Delivered(true),
                                LeaveAtDoor.NOT_ALLOWED,
                                LATER),
                        new NotALeaveAtDoorOrder(STOP)),
                arguments(
                        "on behalf, repeating failed",
                        failed,
                        (Consumer<Delivery>) d -> d.recordOnBehalf(
                                DISPATCHER,
                                ROUTE_A,
                                new DispatcherOutcome.Failed(ABSENT),
                                LeaveAtDoor.NOT_ALLOWED,
                                LATER),
                        new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.FAILED)),
                arguments(
                        "on behalf, contradicting delivered",
                        delivered,
                        (Consumer<Delivery>) d -> d.recordOnBehalf(
                                DISPATCHER,
                                ROUTE_A,
                                new DispatcherOutcome.Failed(ABSENT),
                                LeaveAtDoor.NOT_ALLOWED,
                                LATER),
                        new OutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED)),
                arguments(
                        "correct, left at the door of an order that may not be, with no outcome: the door comes first",
                        fresh,
                        (Consumer<Delivery>) d -> d.correct(
                                new DispatcherOutcome.Delivered(true),
                                TYPO,
                                Optional.empty(),
                                DISPATCHER,
                                new LaterAttempt.None(),
                                LeaveAtDoor.NOT_ALLOWED,
                                LATER),
                        new NotALeaveAtDoorOrder(STOP)),
                arguments(
                        "correct, no outcome",
                        arrived,
                        (Consumer<Delivery>) d -> correctToDelivered(d, new LaterAttempt.None()),
                        new NoOutcomeToCorrect(STOP)),
                arguments(
                        "correct, to the same outcome",
                        delivered,
                        (Consumer<Delivery>) d -> correctToDelivered(d, new LaterAttempt.None()),
                        new CorrectionChangesNothing(STOP, OutcomeKind.DELIVERED)),
                arguments(
                        "correct, failed to failed with the same reason",
                        failed,
                        (Consumer<Delivery>) d -> d.correct(
                                new DispatcherOutcome.Failed(FailureReason.of(FailureReasonKind.CUSTOMER_ABSENT)),
                                TYPO,
                                Optional.empty(),
                                DISPATCHER,
                                new LaterAttempt.None(),
                                LeaveAtDoor.NOT_ALLOWED,
                                LATER),
                        new CorrectionChangesNothing(STOP, OutcomeKind.FAILED)),
                arguments(
                        "correct, failed to delivered when the put-back's stop has an outcome",
                        failed,
                        (Consumer<Delivery>) d -> correctToDelivered(
                                d, new LaterAttempt.WithOutcome(LATER_STOP, SECOND, OutcomeKind.FAILED)),
                        new LaterAttemptHasOutcome(STOP, LATER_STOP, SECOND, OutcomeKind.FAILED)),
                arguments(
                        "confirm, an unknown report",
                        (Supplier<Delivery>) () -> aDelivery().held(),
                        (Consumer<Delivery>) d -> d.confirmHeld(HELD_2, DISPATCHER, LATER),
                        new ReportNotHeld(STOP, HELD_2)),
                arguments(
                        "confirm, a refused report",
                        (Supplier<Delivery>) () -> aDelivery().pendingAfterRefusal(),
                        (Consumer<Delivery>) d -> d.confirmHeld(HELD_1, DISPATCHER, LATER),
                        new ReportNotHeld(STOP, HELD_1)),
                arguments(
                        "refuse, an unknown report",
                        (Supplier<Delivery>) () -> aDelivery().held(),
                        (Consumer<Delivery>) d -> d.refuseHeld(HELD_2, DISPATCHER, LATER),
                        new ReportNotHeld(STOP, HELD_2)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void GivenABrokenRule_WhenTheCommandRuns_ThenItIsRefusedWithItsErrorAndNothingChanges(
            String name, Supplier<Delivery> state, Consumer<Delivery> command, DomainError expected) {
        Delivery delivery = state.get();

        assertRefusedUnchanged(delivery, () -> command.accept(delivery), expected);
    }

    @Test
    void GivenAReportTimedAtTheCancellation_WhenReported_ThenReportAfterCancellation() {
        Delivery delivery = aDelivery().fresh();

        assertRefusedUnchanged(
                delivery,
                () -> delivery.arrive(byA(ONLINE), ON_ROUTE, CANCELLED_AT_0800),
                new ReportAfterCancellation(STOP, CancellationKind.ORDER_CANCELLED));
    }

    @Test
    void GivenAReportTimedOneSecondBeforeTheCancellation_WhenReported_ThenItIsAccepted() {
        Delivery delivery = aDelivery().fresh();
        StopWhereabouts cancelledJustAfter =
                new StopWhereabouts.Cancelled(CancellationKind.PLAN_CANCELLED, Instant.parse("2027-03-01T08:00:01Z"));

        delivery.arrive(byA(ONLINE), ON_ROUTE, cancelledJustAfter);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.ARRIVED);
    }

    @Test
    void GivenAStopNotOnTheReportsRoute_WhenArrived_ThenStopNotOnYourRoute() {
        Delivery delivery = aDelivery().fresh();

        assertRefusedUnchanged(
                delivery,
                () -> delivery.arrive(byA(ONLINE), NOT_ON_ROUTE, SERVING),
                new StopNotOnYourRoute(STOP, ROUTE_A));
    }

    @Test
    void GivenADeliveredStop_WhenArrived_ThenArrivalAfterOutcome() {
        Delivery delivery = aDelivery().delivered();

        assertRefusedUnchanged(
                delivery, () -> delivery.arrive(byA(ONLINE), ON_ROUTE, SERVING), new ArrivalAfterOutcome(STOP));
    }

    @Test
    void GivenAnArrivedStop_WhenArrivedAgain_ThenArrivalAlreadyRecorded() {
        Delivery delivery = aDelivery().arrived();

        assertRefusedUnchanged(
                delivery, () -> delivery.arrive(byA(ONLINE), ON_ROUTE, SERVING), new ArrivalAlreadyRecorded(STOP));
    }

    @Test
    void GivenADeliveredReportWithNoProof_WhenDelivered_ThenProofRequired() {
        Delivery delivery = aDelivery().fresh();

        assertRefusedUnchanged(
                delivery,
                () -> deliverOnline(delivery, Optional.empty(), false, LeaveAtDoor.NOT_ALLOWED),
                new ProofRequired(STOP));
    }

    @Test
    void GivenAnOrderThatMayNotBeLeftAtTheDoor_WhenDeliveredAtTheDoor_ThenNotALeaveAtDoorOrder() {
        Delivery delivery = aDelivery().fresh();

        assertRefusedUnchanged(
                delivery,
                () -> deliverOnline(delivery, Optional.of(PHOTO), true, LeaveAtDoor.NOT_ALLOWED),
                new NotALeaveAtDoorOrder(STOP));
    }

    @Test
    void GivenADoorDeliveryWithASignature_WhenDelivered_ThenLeftAtDoorNeedsPhoto() {
        Delivery delivery = aDelivery().fresh();

        assertRefusedUnchanged(
                delivery,
                () -> deliverOnline(delivery, Optional.of(SIGNATURE), true, LeaveAtDoor.ALLOWED),
                new LeftAtDoorNeedsPhoto(STOP));
    }

    @Test
    void GivenADeliveredStop_WhenDeliveredAgain_ThenSameOutcomeAlreadyRecorded() {
        Delivery delivery = aDelivery().delivered();

        assertRefusedUnchanged(
                delivery,
                () -> deliverOnline(delivery, Optional.of(SIGNATURE), false, LeaveAtDoor.NOT_ALLOWED),
                new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED));
    }

    @Test
    void GivenADeliveredStop_WhenFailed_ThenOutcomeAlreadyRecorded() {
        Delivery delivery = aDelivery().delivered();

        assertRefusedUnchanged(
                delivery,
                () -> delivery.fail(byA(ONLINE), ABSENT, ON_ROUTE, NO_HOLD, HELD_2, LATER),
                new OutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED));
    }

    @Test
    void GivenAnArrivedStop_WhenCorrected_ThenNoOutcomeToCorrect() {
        Delivery delivery = aDelivery().arrived();

        assertRefusedUnchanged(
                delivery, () -> correctToDelivered(delivery, new LaterAttempt.None()), new NoOutcomeToCorrect(STOP));
    }

    @Test
    void GivenADeliveredStop_WhenCorrectedToDelivered_ThenCorrectionChangesNothing() {
        Delivery delivery = aDelivery().delivered();

        assertRefusedUnchanged(
                delivery,
                () -> correctToDelivered(delivery, new LaterAttempt.None()),
                new CorrectionChangesNothing(STOP, OutcomeKind.DELIVERED));
    }

    @Test
    void GivenAPutBackStopWithAnOutcome_WhenTheFailedStopIsCorrectedToDelivered_ThenLaterAttemptHasOutcome() {
        Delivery delivery = aDelivery().failed();

        assertRefusedUnchanged(
                delivery,
                () -> correctToDelivered(
                        delivery, new LaterAttempt.WithOutcome(LATER_STOP, SECOND, OutcomeKind.DELIVERED)),
                new LaterAttemptHasOutcome(STOP, LATER_STOP, SECOND, OutcomeKind.DELIVERED));
    }

    @Test
    void GivenAConfirmedReport_WhenConfirmedAgain_ThenReportNotHeld() {
        Delivery delivery = aDelivery().held();
        delivery.confirmHeld(HELD_1, DISPATCHER, LATER);
        Delivery reloaded = asSaved(delivery);

        assertRefusedUnchanged(
                reloaded, () -> reloaded.confirmHeld(HELD_1, DISPATCHER, LATER), new ReportNotHeld(STOP, HELD_1));
    }

    @Test
    void GivenAProofWhoseFileWasErased_WhenDelivered_ThenProofHasNoFile() {
        Delivery delivery = aDelivery().fresh();

        assertRefusedUnchanged(
                delivery,
                () -> deliverOnline(delivery, Optional.of(SIGNATURE.erased()), false, LeaveAtDoor.NOT_ALLOWED),
                new ProofHasNoFile(STOP));
    }

    @Test
    void GivenAStatedTimeOneSecondAfterNow_WhenCorrected_ThenCorrectionTimeInFuture() {
        Delivery delivery = aDelivery().failed();

        assertRefusedUnchanged(
                delivery, () -> correctToDeliveredAt(delivery, LATER.plusSeconds(1)), new CorrectionTimeInFuture(STOP));
    }

    @Test
    void GivenAStatedTimeAtNow_WhenCorrected_ThenItIsAccepted() {
        Delivery delivery = aDelivery().failed();

        correctToDeliveredAt(delivery, LATER);

        assertThat(delivery.outcomeTime()).contains(LATER);
    }

    @Test
    void GivenADriversOwnHeldReport_WhenTheyRepeatIt_ThenSameOutcomeAlreadyRecordedAndNoSecondHold() {
        Delivery delivery = aDelivery().held();

        assertRefusedUnchanged(
                delivery,
                () -> delivery.deliver(
                        byB(LATE),
                        Optional.of(SIGNATURE),
                        false,
                        LeaveAtDoor.NOT_ALLOWED,
                        ON_ROUTE_B,
                        MOVED_TO_A_WITH_CONTACT,
                        HELD_2,
                        LATER),
                new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED));
        assertThat(delivery.heldReports()).hasSize(1);
    }

    @Test
    void GivenAPutBackOfAnEarlierAttempt_WhenCorrected_ThenItFailsAsABugNotARefusal() {
        Delivery delivery = aDelivery().failed();
        LaterAttempt earlier = new LaterAttempt.WithoutOutcome(LATER_STOP, LiveOpsFixtures.FIRST, LiveOpsFixtures.DATE);

        assertThatThrownBy(() -> correctToDelivered(delivery, earlier)).isInstanceOf(AssertionError.class);
    }

    @Test
    void GivenAMissingParameter_WhenEachCommandRuns_ThenItFailsLoudlyBeforeAnyRule() {
        Delivery delivery = aDelivery().fresh();

        assertThatThrownBy(() -> delivery.arrive(byA(ONLINE), ON_ROUTE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> delivery.deliver(
                        byA(ONLINE), Optional.empty(), false, LeaveAtDoor.ALLOWED, ON_ROUTE, null, HELD_2, LATER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> delivery.fail(byA(ONLINE), ABSENT, ON_ROUTE, NO_HOLD, HELD_2, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> delivery.recordOnBehalf(
                        DISPATCHER, null, new DispatcherOutcome.Failed(ABSENT), LeaveAtDoor.ALLOWED, LATER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> delivery.correct(
                        new DispatcherOutcome.Failed(ABSENT),
                        TYPO,
                        Optional.empty(),
                        DISPATCHER,
                        null,
                        LeaveAtDoor.ALLOWED,
                        LATER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> delivery.confirmHeld(HELD_1, null, LATER)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> delivery.refuseHeld(HELD_1, DISPATCHER, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> delivery.eraseExpired(null)).isInstanceOf(NullPointerException.class);
        assertThat(delivery.outcomes()).isEmpty();
    }
}
