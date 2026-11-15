package com.dispatchflow.liveops.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainAssertions;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Shared test data for Live Operations: fixed IDs and instants on Monday 1 March 2027 (UTC+2), and
 * one builder per aggregate. Each builder state is reached through the aggregate's own commands and
 * then reloaded, so every state is one {@code restore} accepts.
 */
final class LiveOpsFixtures {

    static final StopId STOP = StopId.of(uuid(1));
    static final StopId LATER_STOP = StopId.of(uuid(2));
    static final OrderId ORDER = OrderId.of(uuid(3));
    static final Attempt FIRST = Attempt.of(1);
    static final Attempt SECOND = Attempt.of(2);
    static final AccountId DRIVER_A = AccountId.of(uuid(10));
    static final AccountId DRIVER_B = AccountId.of(uuid(11));
    static final AccountId DRIVER_C = AccountId.of(uuid(13));
    static final AccountId DISPATCHER = AccountId.of(uuid(12));
    static final RouteId ROUTE_A = RouteId.of(uuid(20));
    static final RouteId ROUTE_B = RouteId.of(uuid(21));
    static final RouteId ROUTE_C = RouteId.of(uuid(22));
    static final PlanId PLAN = PlanId.of(uuid(30));
    static final HeldReportId HELD_1 = HeldReportId.of(uuid(40));
    static final HeldReportId HELD_2 = HeldReportId.of(uuid(41));
    static final HeldReportId HELD_3 = HeldReportId.of(uuid(42));
    static final ProofId SIGNATURE_FILE = ProofId.of(uuid(50));
    static final ProofId PHOTO_FILE = ProofId.of(uuid(51));
    static final RevisionNo REV_1 = RevisionNo.of(1);
    static final RevisionNo REV_2 = RevisionNo.of(2);
    static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 3, 1));

    /** 10:00 local: the report the late cases send. */
    static final Instant T_0800 = Instant.parse("2027-03-01T08:00:00Z");

    static final Instant T_0900 = Instant.parse("2027-03-01T09:00:00Z");
    static final Instant NOW = Instant.parse("2027-03-01T10:00:00Z");
    static final Instant LATER = Instant.parse("2027-03-01T10:30:00Z");

    /** Sent online: corrected time 08:00, received at once, so no contact window. */
    static final CorrectedTime ONLINE = CorrectedTime.of(T_0800, T_0800, T_0800);

    /** Made offline at 08:00 and received at 10:00: window 08:02 to 09:58. */
    static final CorrectedTime LATE = CorrectedTime.of(T_0800, NOW, NOW);

    static final Proof SIGNATURE = Proof.of(ProofKind.SIGNATURE, SIGNATURE_FILE, Optional.empty());
    static final Proof PHOTO = Proof.of(ProofKind.PHOTO, PHOTO_FILE, ProofNote.of("by the blue door"));
    static final FailureReason ABSENT = FailureReason.of(FailureReasonKind.CUSTOMER_ABSENT);
    /** Driver A's route held the stop in revision 1, with A driving it. */
    static final Entitlement ON_ROUTE = new Entitlement.OnRoute(REV_1, DRIVER_A);

    static final Entitlement ON_ROUTE_B = new Entitlement.OnRoute(REV_1, DRIVER_B);
    static final Entitlement ON_ROUTE_C = new Entitlement.OnRoute(REV_1, DRIVER_C);
    static final Entitlement NOT_ON_ROUTE = new Entitlement.NotOnRoute();
    static final StopWhereabouts SERVING = new StopWhereabouts.OnServingRoute();
    static final StopWhereabouts ON_B = new StopWhereabouts.OnAnotherRoute(ROUTE_B);
    static final HoldCheck NO_HOLD = HoldCheck.of(SERVING, List.of());
    static final HoldCheck MOVED_WITH_CONTACT = HoldCheck.of(ON_B, List.of(T_0900));
    static final HoldCheck MOVED_TO_A_WITH_CONTACT =
            HoldCheck.of(new StopWhereabouts.OnAnotherRoute(ROUTE_A), List.of(T_0900));
    static final CorrectionReason TYPO = CorrectionReason.of("driver tapped the wrong button");

    private LiveOpsFixtures() {}

    static UUID uuid(int n) {
        return UUID.fromString(String.format("0190a5e2-0000-7000-a000-%012d", n));
    }

    static DriverReport byA(CorrectedTime time) {
        return new DriverReport(DRIVER_A, ROUTE_A, time);
    }

    static DriverReport byB(CorrectedTime time) {
        return new DriverReport(DRIVER_B, ROUTE_B, time);
    }

    static DriverReport byC(CorrectedTime time) {
        return new DriverReport(DRIVER_C, ROUTE_C, time);
    }

    /** Asserts a refusal, and that it changed neither the state nor the pending events. */
    static void assertRefusedUnchanged(Delivery delivery, ThrowingCallable call, DomainError expected) {
        DeliverySnapshot before = delivery.snapshot();
        List<DomainEvent> events = delivery.pendingEvents();

        DomainAssertions.assertRefused(call, expected);

        assertThat(delivery.snapshot()).isEqualTo(before);
        assertThat(delivery.pendingEvents()).isEqualTo(events);
    }

    static Delivery reload(Delivery delivery) {
        DeliverySnapshot s = delivery.snapshot();
        return Delivery.restore(new DeliverySnapshot(
                s.stop(),
                s.order(),
                s.attempt(),
                delivery.version().next(),
                s.arrival(),
                s.outcomes(),
                s.heldReports()));
    }

    static RouteRun reload(RouteRun run) {
        RouteRunSnapshot s = run.snapshot();
        return RouteRun.restore(new RouteRunSnapshot(
                s.route(), run.version().next(), s.startedBy(), s.cause(), s.startedAt(), s.status(), s.confirmed()));
    }

    static DeliveryBuilder aDelivery() {
        return new DeliveryBuilder();
    }

    /** Builds a delivery in each state of DL §9.3 through its commands, saved and reloaded. */
    static final class DeliveryBuilder {

        private DeliveryBuilder() {}

        Delivery fresh() {
            return Delivery.firstFact(STOP, ORDER, FIRST);
        }

        Delivery arrived() {
            Delivery delivery = fresh();
            delivery.arrive(byA(ONLINE), ON_ROUTE, SERVING);
            return reload(delivery);
        }

        Delivery delivered() {
            Delivery delivery = fresh();
            delivery.deliver(
                    byA(ONLINE),
                    Optional.of(SIGNATURE),
                    false,
                    LeaveAtDoor.NOT_ALLOWED,
                    ON_ROUTE,
                    NO_HOLD,
                    HELD_1,
                    NOW);
            return reload(delivery);
        }

        Delivery failed() {
            Delivery delivery = fresh();
            delivery.fail(byA(ONLINE), ABSENT, ON_ROUTE, NO_HOLD, HELD_1, NOW);
            return reload(delivery);
        }

        /**
         * Driver B's late *delivered* for a stop now on A's route, held as {@link #HELD_1}; A's own
         * reports are then on the stop's current route.
         */
        Delivery held() {
            Delivery delivery = fresh();
            holdLate(delivery, HELD_1);
            return reload(delivery);
        }

        Delivery heldAfterArrival() {
            Delivery delivery = fresh();
            delivery.arrive(byA(ONLINE), ON_ROUTE, SERVING);
            holdLate(delivery, HELD_1);
            return reload(delivery);
        }

        /** Held, then refused by a dispatcher: pending, with the refused report on record. */
        Delivery pendingAfterRefusal() {
            Delivery delivery = held();
            delivery.refuseHeld(HELD_1, DISPATCHER, NOW);
            return reload(delivery);
        }

        Delivery failedThenCorrected() {
            Delivery delivery = failed();
            delivery.correct(
                    new DispatcherOutcome.Delivered(false),
                    TYPO,
                    Optional.empty(),
                    DISPATCHER,
                    new LaterAttempt.None(),
                    LeaveAtDoor.NOT_ALLOWED,
                    NOW);
            return reload(delivery);
        }

        static void holdLate(Delivery delivery, HeldReportId id) {
            delivery.deliver(
                    byB(LATE),
                    Optional.of(SIGNATURE),
                    false,
                    LeaveAtDoor.NOT_ALLOWED,
                    ON_ROUTE_B,
                    MOVED_TO_A_WITH_CONTACT,
                    id,
                    NOW);
        }
    }
}
