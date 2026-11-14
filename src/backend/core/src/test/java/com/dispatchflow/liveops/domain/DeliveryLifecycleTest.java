package com.dispatchflow.liveops.domain;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ABSENT;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DISPATCHER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_1;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.HELD_2;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATER;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NO_HOLD;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ON_ROUTE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ROUTE_A;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SERVING;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SIGNATURE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.STOP;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.TYPO;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.aDelivery;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.assertRefusedUnchanged;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.byA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.dispatchflow.liveops.domain.DeliverySnapshot.HeldReport;
import com.dispatchflow.liveops.domain.DeliverySnapshot.HeldState;
import com.dispatchflow.liveops.domain.LiveOpsError.ArrivalAfterOutcome;
import com.dispatchflow.liveops.domain.LiveOpsError.ArrivalAlreadyRecorded;
import com.dispatchflow.liveops.domain.LiveOpsError.CorrectionChangesNothing;
import com.dispatchflow.liveops.domain.LiveOpsError.NoOutcomeToCorrect;
import com.dispatchflow.liveops.domain.LiveOpsError.OutcomeAlreadyRecorded;
import com.dispatchflow.liveops.domain.LiveOpsError.ReportNotHeld;
import com.dispatchflow.liveops.domain.LiveOpsError.SameOutcomeAlreadyRecorded;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every state of a delivery against every command (DL §9.3, BR-87): an accepted cell checks the
 * resulting state, the events raised, the command's answer, the arrival and the held reports'
 * states; a refused cell checks the named error with the state and the events unchanged. Each cell
 * runs its command alone on a reloaded state.
 */
class DeliveryLifecycleTest {

    private static final List<String> NONE = List.of();
    private static final List<String> RECORDED = List.of("OutcomeRecorded");
    private static final Optional<ReportAnswer> NO_ANSWER = Optional.empty();
    private static final Optional<ReportAnswer> RECORDED_ALONE = Optional.of(new ReportAnswer.Recorded(List.of()));
    private static final Optional<ReportAnswer> RECORDED_OVER_HELD_1 =
            Optional.of(new ReportAnswer.Recorded(List.of(HELD_1)));
    private static final Optional<Instant> NO_ARRIVAL = Optional.empty();
    private static final Optional<Instant> ARRIVED_0800 = Optional.of(T_0800);
    private static final List<HeldState> NO_HELD = List.of();
    private static final List<HeldState> OPEN = List.of(new HeldState.Open());
    private static final List<HeldState> REFUSED_EARLIER = List.of(new HeldState.RefusedByDispatcher(DISPATCHER, NOW));
    private static final List<HeldState> REFUSED_NOW = List.of(new HeldState.RefusedByDispatcher(DISPATCHER, LATER));
    private static final List<HeldState> OVERTAKEN = List.of(new HeldState.Overtaken(LATER));
    private static final List<HeldState> CONFIRMED = List.of(new HeldState.Confirmed(DISPATCHER, LATER));

    enum State {
        NEW(() -> aDelivery().fresh()),
        PENDING(() -> aDelivery().pendingAfterRefusal()),
        ARRIVED(() -> aDelivery().arrived()),
        HELD(() -> aDelivery().held()),
        HELD_ARRIVED(() -> aDelivery().heldAfterArrival()),
        DELIVERED(() -> aDelivery().delivered()),
        FAILED(() -> aDelivery().failed());

        private final Supplier<Delivery> build;

        State(Supplier<Delivery> build) {
            this.build = build;
        }
    }

    enum Command {
        ARRIVE(delivery -> {
            delivery.arrive(byA(ONLINE), ON_ROUTE, SERVING);
            return Optional.empty();
        }),
        DELIVER(delivery -> Optional.of(delivery.deliver(
                byA(ONLINE),
                Optional.of(SIGNATURE),
                false,
                LeaveAtDoor.NOT_ALLOWED,
                ON_ROUTE,
                NO_HOLD,
                HELD_2,
                LATER))),
        FAIL(delivery -> Optional.of(delivery.fail(byA(ONLINE), ABSENT, ON_ROUTE, NO_HOLD, HELD_2, LATER))),
        ON_BEHALF(delivery -> Optional.of(delivery.recordOnBehalf(
                DISPATCHER, ROUTE_A, new DispatcherOutcome.Delivered(false), LeaveAtDoor.NOT_ALLOWED, LATER))),
        CORRECT_TO_DELIVERED(delivery -> {
            delivery.correct(
                    new DispatcherOutcome.Delivered(false),
                    TYPO,
                    Optional.empty(),
                    DISPATCHER,
                    new LaterAttempt.None(),
                    LeaveAtDoor.NOT_ALLOWED,
                    LATER);
            return Optional.empty();
        }),
        CONFIRM(delivery -> Optional.of(delivery.confirmHeld(HELD_1, DISPATCHER, LATER))),
        REFUSE(delivery -> {
            delivery.refuseHeld(HELD_1, DISPATCHER, LATER);
            return Optional.empty();
        });

        private final Function<Delivery, Optional<ReportAnswer>> run;

        Command(Function<Delivery, Optional<ReportAnswer>> run) {
            this.run = run;
        }
    }

    private static Arguments cell(
            State state,
            Command command,
            DeliveryStatus status,
            List<String> events,
            Optional<ReportAnswer> answer,
            Optional<Instant> arrivedAt,
            List<HeldState> held) {
        return arguments(state, command, status, events, answer, arrivedAt, held);
    }

    static Stream<Arguments> accepted() {
        return Stream.of(
                cell(State.NEW, Command.ARRIVE, DeliveryStatus.ARRIVED, NONE, NO_ANSWER, ARRIVED_0800, NO_HELD),
                cell(
                        State.PENDING,
                        Command.ARRIVE,
                        DeliveryStatus.ARRIVED,
                        NONE,
                        NO_ANSWER,
                        ARRIVED_0800,
                        REFUSED_EARLIER),
                cell(State.HELD, Command.ARRIVE, DeliveryStatus.HELD, NONE, NO_ANSWER, ARRIVED_0800, OPEN),
                cell(
                        State.NEW,
                        Command.DELIVER,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_ALONE,
                        NO_ARRIVAL,
                        NO_HELD),
                cell(
                        State.PENDING,
                        Command.DELIVER,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_ALONE,
                        NO_ARRIVAL,
                        REFUSED_EARLIER),
                cell(
                        State.ARRIVED,
                        Command.DELIVER,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_ALONE,
                        ARRIVED_0800,
                        NO_HELD),
                cell(
                        State.HELD,
                        Command.DELIVER,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_OVER_HELD_1,
                        NO_ARRIVAL,
                        OVERTAKEN),
                cell(
                        State.HELD_ARRIVED,
                        Command.DELIVER,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_OVER_HELD_1,
                        ARRIVED_0800,
                        OVERTAKEN),
                cell(State.NEW, Command.FAIL, DeliveryStatus.FAILED, RECORDED, RECORDED_ALONE, NO_ARRIVAL, NO_HELD),
                cell(
                        State.PENDING,
                        Command.FAIL,
                        DeliveryStatus.FAILED,
                        RECORDED,
                        RECORDED_ALONE,
                        NO_ARRIVAL,
                        REFUSED_EARLIER),
                cell(
                        State.ARRIVED,
                        Command.FAIL,
                        DeliveryStatus.FAILED,
                        RECORDED,
                        RECORDED_ALONE,
                        ARRIVED_0800,
                        NO_HELD),
                cell(
                        State.HELD,
                        Command.FAIL,
                        DeliveryStatus.FAILED,
                        RECORDED,
                        RECORDED_OVER_HELD_1,
                        NO_ARRIVAL,
                        OVERTAKEN),
                cell(
                        State.HELD_ARRIVED,
                        Command.FAIL,
                        DeliveryStatus.FAILED,
                        RECORDED,
                        RECORDED_OVER_HELD_1,
                        ARRIVED_0800,
                        OVERTAKEN),
                cell(
                        State.NEW,
                        Command.ON_BEHALF,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_ALONE,
                        NO_ARRIVAL,
                        NO_HELD),
                cell(
                        State.PENDING,
                        Command.ON_BEHALF,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_ALONE,
                        NO_ARRIVAL,
                        REFUSED_EARLIER),
                cell(
                        State.ARRIVED,
                        Command.ON_BEHALF,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_ALONE,
                        ARRIVED_0800,
                        NO_HELD),
                cell(
                        State.HELD,
                        Command.ON_BEHALF,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_OVER_HELD_1,
                        NO_ARRIVAL,
                        OVERTAKEN),
                cell(
                        State.HELD_ARRIVED,
                        Command.ON_BEHALF,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_OVER_HELD_1,
                        ARRIVED_0800,
                        OVERTAKEN),
                cell(
                        State.FAILED,
                        Command.CORRECT_TO_DELIVERED,
                        DeliveryStatus.DELIVERED,
                        NONE,
                        NO_ANSWER,
                        NO_ARRIVAL,
                        NO_HELD),
                cell(
                        State.HELD,
                        Command.CONFIRM,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_ALONE,
                        NO_ARRIVAL,
                        CONFIRMED),
                cell(
                        State.HELD_ARRIVED,
                        Command.CONFIRM,
                        DeliveryStatus.DELIVERED,
                        RECORDED,
                        RECORDED_ALONE,
                        ARRIVED_0800,
                        CONFIRMED),
                cell(State.HELD, Command.REFUSE, DeliveryStatus.PENDING, NONE, NO_ANSWER, NO_ARRIVAL, REFUSED_NOW),
                cell(
                        State.HELD_ARRIVED,
                        Command.REFUSE,
                        DeliveryStatus.ARRIVED,
                        NONE,
                        NO_ANSWER,
                        ARRIVED_0800,
                        REFUSED_NOW));
    }

    static Stream<Arguments> refused() {
        return Stream.of(
                arguments(State.ARRIVED, Command.ARRIVE, new ArrivalAlreadyRecorded(STOP)),
                arguments(State.HELD_ARRIVED, Command.ARRIVE, new ArrivalAlreadyRecorded(STOP)),
                arguments(State.DELIVERED, Command.ARRIVE, new ArrivalAfterOutcome(STOP)),
                arguments(State.FAILED, Command.ARRIVE, new ArrivalAfterOutcome(STOP)),
                arguments(
                        State.DELIVERED, Command.DELIVER, new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED)),
                arguments(State.FAILED, Command.DELIVER, new OutcomeAlreadyRecorded(STOP, OutcomeKind.FAILED)),
                arguments(State.DELIVERED, Command.FAIL, new OutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED)),
                arguments(State.FAILED, Command.FAIL, new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.FAILED)),
                arguments(
                        State.DELIVERED,
                        Command.ON_BEHALF,
                        new SameOutcomeAlreadyRecorded(STOP, OutcomeKind.DELIVERED)),
                arguments(State.FAILED, Command.ON_BEHALF, new OutcomeAlreadyRecorded(STOP, OutcomeKind.FAILED)),
                arguments(State.NEW, Command.CORRECT_TO_DELIVERED, new NoOutcomeToCorrect(STOP)),
                arguments(State.PENDING, Command.CORRECT_TO_DELIVERED, new NoOutcomeToCorrect(STOP)),
                arguments(State.ARRIVED, Command.CORRECT_TO_DELIVERED, new NoOutcomeToCorrect(STOP)),
                arguments(State.HELD, Command.CORRECT_TO_DELIVERED, new NoOutcomeToCorrect(STOP)),
                arguments(State.HELD_ARRIVED, Command.CORRECT_TO_DELIVERED, new NoOutcomeToCorrect(STOP)),
                arguments(
                        State.DELIVERED,
                        Command.CORRECT_TO_DELIVERED,
                        new CorrectionChangesNothing(STOP, OutcomeKind.DELIVERED)),
                arguments(State.NEW, Command.CONFIRM, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.PENDING, Command.CONFIRM, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.ARRIVED, Command.CONFIRM, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.DELIVERED, Command.CONFIRM, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.FAILED, Command.CONFIRM, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.NEW, Command.REFUSE, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.PENDING, Command.REFUSE, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.ARRIVED, Command.REFUSE, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.DELIVERED, Command.REFUSE, new ReportNotHeld(STOP, HELD_1)),
                arguments(State.FAILED, Command.REFUSE, new ReportNotHeld(STOP, HELD_1)));
    }

    @ParameterizedTest(name = "{0} + {1} -> {2} {3} {4}")
    @MethodSource("accepted")
    void GivenAState_WhenAnAllowedCommandRuns_ThenItReachesTheStateAndRaisesTheEvents(
            State state,
            Command command,
            DeliveryStatus expected,
            List<String> events,
            Optional<ReportAnswer> answer,
            Optional<Instant> arrivedAt,
            List<HeldState> held) {
        Delivery delivery = state.build.get();

        Optional<ReportAnswer> actual = command.run.apply(delivery);

        assertThat(delivery.status()).isEqualTo(expected);
        assertThat(delivery.pendingEvents().stream().map(DomainEvent::eventId).map(id -> id.eventType()))
                .containsExactlyElementsOf(events);
        assertThat(actual).isEqualTo(answer);
        assertThat(delivery.arrivedAt()).isEqualTo(arrivedAt);
        assertThat(delivery.snapshot().heldReports())
                .extracting(HeldReport::state)
                .containsExactlyElementsOf(held);
        assertThat(Delivery.restore(delivery.snapshot()).snapshot()).isEqualTo(delivery.snapshot());
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("refused")
    void GivenAState_WhenARefusedCommandRuns_ThenItIsRefusedAndNothingChanges(
            State state, Command command, DomainError expected) {
        Delivery delivery = state.build.get();

        assertRefusedUnchanged(delivery, () -> command.run.apply(delivery), expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyState")
    void GivenEveryCell_WhenCounted_ThenEachStateAndCommandPairIsCoveredOnce(State state) {
        long cells = Stream.concat(accepted(), refused())
                .filter(cell -> cell.get()[0] == state)
                .count();

        assertThat(cells).isEqualTo(Command.values().length);
    }

    static Stream<State> everyState() {
        return Stream.of(State.values());
    }
}
