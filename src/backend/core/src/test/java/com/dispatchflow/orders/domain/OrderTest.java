package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.OrderBuilder.anOrder;
import static com.dispatchflow.orders.domain.OrdersTestData.CUSTOMER;
import static com.dispatchflow.orders.domain.OrdersTestData.DEFAULTS;
import static com.dispatchflow.orders.domain.OrdersTestData.LATER;
import static com.dispatchflow.orders.domain.OrdersTestData.NEXT_WEEK;
import static com.dispatchflow.orders.domain.OrdersTestData.NINE_TO_FIVE;
import static com.dispatchflow.orders.domain.OrdersTestData.NOW;
import static com.dispatchflow.orders.domain.OrdersTestData.NO_DEFAULT_SERVICE_TIME;
import static com.dispatchflow.orders.domain.OrdersTestData.ORDER;
import static com.dispatchflow.orders.domain.OrdersTestData.REFERENCE;
import static com.dispatchflow.orders.domain.OrdersTestData.TODAY;
import static com.dispatchflow.orders.domain.OrdersTestData.TODAY_DATE;
import static com.dispatchflow.orders.domain.OrdersTestData.TOMORROW;
import static com.dispatchflow.orders.domain.OrdersTestData.YESTERDAY;
import static com.dispatchflow.orders.domain.OrdersTestData.bareDetails;
import static com.dispatchflow.orders.domain.OrdersTestData.dateWith;
import static com.dispatchflow.orders.domain.OrdersTestData.dateWithAnd;
import static com.dispatchflow.orders.domain.OrdersTestData.dateWithReference;
import static com.dispatchflow.orders.domain.OrdersTestData.details;
import static com.dispatchflow.orders.domain.OrdersTestData.emptyDate;
import static com.dispatchflow.orders.domain.OrdersTestData.kilograms;
import static com.dispatchflow.orders.domain.OrdersTestData.litres;
import static com.dispatchflow.orders.domain.OrdersTestData.tenToNoon;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.orders.domain.OrdersError.AlreadyOnThatDate;
import com.dispatchflow.orders.domain.OrdersError.DateFull;
import com.dispatchflow.orders.domain.OrdersError.DateInPast;
import com.dispatchflow.orders.domain.OrdersError.DuplicateOrderReference;
import com.dispatchflow.orders.domain.OrdersError.NoServiceTime;
import com.dispatchflow.orders.domain.OrdersError.OnlyFailedOrDroppedOrdersArePutBack;
import com.dispatchflow.orders.domain.OrdersError.OrderAlreadyCancelled;
import com.dispatchflow.orders.domain.OrdersError.OrderAnonymized;
import com.dispatchflow.orders.domain.OrdersError.RetentionNotOver;
import com.dispatchflow.orders.domain.OrdersError.StopAlreadyHasFact;
import com.dispatchflow.orders.domain.OrdersError.WindowNotOnDate;
import com.dispatchflow.orders.domain.Placement.PlanStanding;
import com.dispatchflow.orders.events.OrderCancelled;
import com.dispatchflow.orders.events.OrderRescheduled;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link Order}'s invariants (DL §4.3) and its REQ-wins rows (ADR-102 and the plan's Appendix A). */
class OrderTest {

    private static final StopState NO_FACT = new StopState.NoFact();
    private static final Placement NOT_PLACED = new Placement.NotPlaced();
    private static final Placement DROPPED_IN_FORCE = new Placement.Dropped(Attempt.FIRST, PlanStanding.IN_FORCE);
    private static final StopState FAILED_FIRST = new StopState.Failed(Attempt.FIRST);

    /** A refused call leaves the order and its pending events as they were. */
    private static void assertRefusedUnchanged(Order order, ThrowingCallable call, DomainError expected) {
        OrderSnapshot before = order.snapshot();
        assertRefused(call, expected);
        assertThat(order.snapshot()).isEqualTo(before);
        assertThat(order.pendingEvents()).isEmpty();
    }

    private static OrderChange changeOn(DeliveryDate date) {
        return new OrderChange(
                TimeWindow.on(date, LocalTime.of(13, 0), LocalTime.of(15, 0)),
                kilograms("3"),
                litres("4"),
                ServiceTime.ofSeconds(60),
                Priority.of(5),
                false,
                Recipient.NONE,
                Optional.empty(),
                AccessDetails.NONE);
    }

    @Nested
    class Placing {

        @Test
        void GivenValidDetails_WhenPlaced_ThenTheOrderHoldsThemAtTheFirstAttemptWithNoEvent() {
            Order order = Order.place(ORDER, CUSTOMER, details(TOMORROW), DEFAULTS, emptyDate(TOMORROW), TODAY, NOW);

            OrderSnapshot placed = order.snapshot();
            assertThat(placed.date()).isEqualTo(TOMORROW);
            assertThat(placed.window()).isEqualTo(tenToNoon(TOMORROW));
            assertThat(placed.serviceTime()).isEqualTo(ServiceTime.ofSeconds(180));
            assertThat(placed.attempt()).isEqualTo(Attempt.FIRST);
            assertThat(placed.state()).isEqualTo(new Order.State.Active());
            assertThat(placed.lastChangedAt()).isEqualTo(NOW);
            assertThat(placed.version()).isEqualTo(Version.NEW);
            assertThat(order.pendingEvents()).isEmpty();
        }

        @Test
        void GivenTodayItself_WhenPlaced_ThenTheDateIsNotPast() {
            Order order =
                    Order.place(ORDER, CUSTOMER, details(TODAY_DATE), DEFAULTS, emptyDate(TODAY_DATE), TODAY, NOW);

            assertThat(order.date()).isEqualTo(TODAY_DATE);
        }

        @Test
        void GivenNoWindowAndNoServiceTime_WhenPlaced_ThenTheCustomersDefaultsApply() {
            Order order = Order.place(ORDER, CUSTOMER, bareDetails(), DEFAULTS, emptyDate(TOMORROW), TODAY, NOW);

            assertThat(order.window()).isEqualTo(NINE_TO_FIVE.on(TOMORROW));
            assertThat(order.snapshot().serviceTime()).isEqualTo(OrdersTestData.FIVE_MINUTES);
        }

        @Test
        void GivenNoServiceTimeAndNoDefaultOne_WhenPlaced_ThenNoServiceTime() {
            assertRefused(
                    () -> Order.place(
                            ORDER, CUSTOMER, bareDetails(), NO_DEFAULT_SERVICE_TIME, emptyDate(TOMORROW), TODAY, NOW),
                    new NoServiceTime());
        }

        @Test
        void GivenAPastDate_WhenPlaced_ThenDateInPast() {
            assertRefused(
                    () -> Order.place(ORDER, CUSTOMER, details(YESTERDAY), DEFAULTS, emptyDate(YESTERDAY), TODAY, NOW),
                    new DateInPast(YESTERDAY));
        }

        @Test
        void GivenAWindowOnAnotherDate_WhenPlaced_ThenWindowNotOnDate() {
            OrderDetails offDate = details(NEXT_WEEK);

            assertRefused(
                    () -> Order.place(ORDER, CUSTOMER, offDate, DEFAULTS, emptyDate(TOMORROW), TODAY, NOW),
                    new WindowNotOnDate(tenToNoon(NEXT_WEEK), TOMORROW));
        }

        @Test
        void GivenADefaultWindowInTheSkippedHour_WhenPlaced_ThenNonexistentLocalTime() {
            DeliveryDate springForward = DeliveryDate.of(LocalDate.of(2027, 3, 26));
            OrderDefaults earlyHours =
                    new OrderDefaults(DailyWindow.of(LocalTime.of(2, 30), LocalTime.of(4, 0)), Optional.empty());

            assertRefused(
                    () -> Order.place(
                            ORDER,
                            CUSTOMER,
                            bareDetails(),
                            earlyHours,
                            emptyDate(springForward),
                            LocalDate.of(2027, 3, 1),
                            NOW),
                    new NonexistentLocalTime(LocalDate.of(2027, 3, 26).atTime(2, 30)));
        }

        @Test
        void GivenAReferenceAnotherOrderCarriesOnTheDate_WhenPlaced_ThenDuplicateOrderReference() {
            assertRefused(
                    () -> Order.place(
                            ORDER,
                            CUSTOMER,
                            details(TOMORROW),
                            DEFAULTS,
                            dateWithReference(TOMORROW, REFERENCE),
                            TODAY,
                            NOW),
                    new DuplicateOrderReference(TOMORROW));
        }

        @Test
        void GivenADateHolding199Orders_WhenPlaced_ThenThe200thIsPlaced() {
            Order order =
                    Order.place(ORDER, CUSTOMER, details(TOMORROW), DEFAULTS, dateWith(TOMORROW, 199), TODAY, NOW);

            assertThat(order.date()).isEqualTo(TOMORROW);
        }

        @Test
        void GivenADateHolding200Orders_WhenPlaced_ThenDateFull() {
            assertRefused(
                    () -> Order.place(
                            ORDER, CUSTOMER, details(TOMORROW), DEFAULTS, dateWith(TOMORROW, 200), TODAY, NOW),
                    new DateFull(TOMORROW, 200));
        }

        @Test
        void GivenAPastDateAndNoServiceTime_WhenPlaced_ThenThePastDateIsRefusedFirst() {
            assertRefused(
                    () -> Order.place(
                            ORDER, CUSTOMER, bareDetails(), NO_DEFAULT_SERVICE_TIME, emptyDate(YESTERDAY), TODAY, NOW),
                    new DateInPast(YESTERDAY));
        }
    }

    @Nested
    class Changing {

        @Test
        void GivenAnActiveOrder_WhenChanged_ThenItHoldsTheNewValuesAndTheChangeTime() {
            Order order = anOrder().restored();

            OrderSnapshot before = order.snapshot();
            OrderChange change = changeOn(TOMORROW);

            order.change(change, NO_FACT, LATER);

            assertThat(order.snapshot())
                    .isEqualTo(new OrderSnapshot(
                            before.id(),
                            before.customer(),
                            before.date(),
                            change.window(),
                            change.weight(),
                            change.volume(),
                            change.serviceTime(),
                            change.priority(),
                            change.leaveAtDoor(),
                            before.reference(),
                            change.recipient(),
                            change.note(),
                            change.access(),
                            before.attempt(),
                            before.state(),
                            false,
                            LATER,
                            before.version()));
            assertThat(order.pendingEvents()).isEmpty();
        }

        @Test
        void GivenAWindowOnAnotherDate_WhenChanged_ThenWindowNotOnDate() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.change(changeOn(NEXT_WEEK), NO_FACT, LATER),
                    new WindowNotOnDate(changeOn(NEXT_WEEK).window(), TOMORROW));
        }

        @Test
        void GivenAnArrivedStop_WhenChanged_ThenStopAlreadyHasFact() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.change(changeOn(TOMORROW), new StopState.Arrived(Attempt.FIRST), LATER),
                    new StopAlreadyHasFact(ORDER));
        }

        @Test
        void GivenAFactOnlyOnAnEarlierAttempt_WhenChanged_ThenItIsChanged() {
            Order order = anOrder().atAttempt(2).restored();

            order.change(changeOn(TOMORROW), new StopState.Failed(Attempt.FIRST), LATER);

            assertThat(order.lastChangedAt()).isEqualTo(LATER);
        }
    }

    @Nested
    class Rescheduling {

        @Test
        void GivenAnActiveOrder_WhenRescheduled_ThenItMovesAndRaisesOrderRescheduled() {
            Order order = anOrder().restored();

            order.reschedule(tenToNoon(NEXT_WEEK), emptyDate(NEXT_WEEK), NO_FACT, TODAY, LATER);

            assertThat(order.date()).isEqualTo(NEXT_WEEK);
            assertThat(order.window()).isEqualTo(tenToNoon(NEXT_WEEK));
            assertThat(order.attempt()).isEqualTo(Attempt.FIRST);
            assertThat(order.lastChangedAt()).isEqualTo(LATER);
            assertThat(order.pendingEvents())
                    .containsExactly(new OrderRescheduled(
                            new EventId("OrderRescheduled", ORDER.value(), Version.of(4), 0),
                            LATER,
                            ORDER,
                            TOMORROW,
                            NEXT_WEEK,
                            1));
        }

        @Test
        void GivenItsOwnDate_WhenRescheduled_ThenAlreadyOnThatDate() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.reschedule(tenToNoon(TOMORROW), emptyDate(TOMORROW), NO_FACT, TODAY, LATER),
                    new AlreadyOnThatDate(TOMORROW));
        }

        @Test
        void GivenAPastDate_WhenRescheduled_ThenDateInPast() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.reschedule(tenToNoon(YESTERDAY), emptyDate(YESTERDAY), NO_FACT, TODAY, LATER),
                    new DateInPast(YESTERDAY));
        }

        @Test
        void GivenAWindowOffTheNewDate_WhenRescheduled_ThenWindowNotOnDate() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.reschedule(tenToNoon(TOMORROW), emptyDate(NEXT_WEEK), NO_FACT, TODAY, LATER),
                    new WindowNotOnDate(tenToNoon(TOMORROW), NEXT_WEEK));
        }

        @Test
        void GivenItsReferenceTakenOnTheNewDate_WhenRescheduled_ThenDuplicateOrderReference() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.reschedule(
                            tenToNoon(NEXT_WEEK), dateWithReference(NEXT_WEEK, REFERENCE), NO_FACT, TODAY, LATER),
                    new DuplicateOrderReference(NEXT_WEEK));
        }

        @Test
        void GivenAFullNewDate_WhenRescheduled_ThenDateFull() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.reschedule(tenToNoon(NEXT_WEEK), dateWith(NEXT_WEEK, 200), NO_FACT, TODAY, LATER),
                    new DateFull(NEXT_WEEK, 200));
        }

        @Test
        void GivenADeliveredStop_WhenRescheduled_ThenStopAlreadyHasFact() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.reschedule(
                            tenToNoon(NEXT_WEEK),
                            emptyDate(NEXT_WEEK),
                            new StopState.Delivered(Attempt.FIRST),
                            TODAY,
                            LATER),
                    new StopAlreadyHasFact(ORDER));
        }
    }

    @Nested
    class Cancelling {

        @Test
        void GivenAnActiveOrder_WhenCancelled_ThenItIsCancelledAndRaisesOrderCancelled() {
            Order order = anOrder().restored();

            order.cancel(NO_FACT, LATER);

            assertThat(order.isCancelled()).isTrue();
            assertThat(order.snapshot().state()).isEqualTo(new Order.State.Cancelled(LATER));
            assertThat(order.lastChangedAt()).isEqualTo(LATER);
            assertThat(order.pendingEvents())
                    .containsExactly(new OrderCancelled(
                            new EventId("OrderCancelled", ORDER.value(), Version.of(4), 0), LATER, ORDER, TOMORROW, 1));
        }

        @Test
        void GivenACancelledOrder_WhenCancelled_ThenOrderAlreadyCancelled() {
            Order order = anOrder().cancelledAt(NOW).restored();

            assertRefusedUnchanged(order, () -> order.cancel(NO_FACT, LATER), new OrderAlreadyCancelled(ORDER));
        }

        @Test
        void GivenAnArrivedStop_WhenCancelled_ThenStopAlreadyHasFact() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.cancel(new StopState.Arrived(Attempt.FIRST), LATER),
                    new StopAlreadyHasFact(ORDER));
        }
    }

    @Nested
    class PuttingBack {

        @Test
        void GivenAFailedOrder_WhenPutBackOnAnotherDate_ThenItStartsTheNextAttemptThereWithNoEvent() {
            Order order = anOrder().restored();

            order.putBack(
                    new Placement.Planned(Attempt.FIRST, PlanStanding.IN_FORCE),
                    FAILED_FIRST,
                    tenToNoon(NEXT_WEEK),
                    emptyDate(NEXT_WEEK),
                    TODAY,
                    LATER);

            assertThat(order.date()).isEqualTo(NEXT_WEEK);
            assertThat(order.window()).isEqualTo(tenToNoon(NEXT_WEEK));
            assertThat(order.attempt()).isEqualTo(Attempt.of(2));
            assertThat(order.lastChangedAt()).isEqualTo(LATER);
            assertThat(order.pendingEvents()).isEmpty();
        }

        @Test
        void GivenADroppedOrder_WhenPutBack_ThenItStartsTheNextAttempt() {
            Order order = anOrder().restored();

            order.putBack(DROPPED_IN_FORCE, NO_FACT, tenToNoon(NEXT_WEEK), emptyDate(NEXT_WEEK), TODAY, LATER);

            assertThat(order.attempt()).isEqualTo(Attempt.of(2));
            assertThat(order.date()).isEqualTo(NEXT_WEEK);
        }

        @Test
        void GivenADroppedOrderOnAFullDate_WhenPutBackOnItsOwnDate_ThenItDoesNotCountAgain() {
            Order order = anOrder().restored();

            order.putBack(
                    DROPPED_IN_FORCE, NO_FACT, tenToNoon(TOMORROW), dateWithAnd(TOMORROW, 199, ORDER), TODAY, LATER);

            assertThat(order.attempt()).isEqualTo(Attempt.of(2));
            assertThat(order.date()).isEqualTo(TOMORROW);
        }

        @Test
        void GivenADroppedOrderWithAReference_WhenPutBackOnItsOwnDate_ThenItsOwnReferenceDoesNotCount() {
            Order order = anOrder().restored();
            DateCapacity holdingItself = DateCapacity.readUnderLock(
                    TOMORROW, List.of(new DateCapacity.DatedOrder(ORDER, Optional.of(REFERENCE))));

            order.putBack(DROPPED_IN_FORCE, NO_FACT, tenToNoon(TOMORROW), holdingItself, TODAY, LATER);

            assertThat(order.attempt()).isEqualTo(Attempt.of(2));
            assertThat(order.reference()).contains(REFERENCE);
        }

        @Test
        void GivenADroppedOrderOnAFullOtherDate_WhenPutBack_ThenDateFull() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.putBack(
                            DROPPED_IN_FORCE, NO_FACT, tenToNoon(NEXT_WEEK), dateWith(NEXT_WEEK, 200), TODAY, LATER),
                    new DateFull(NEXT_WEEK, 200));
        }

        @Test
        void GivenItsReferenceTakenOnTheTargetDate_WhenPutBack_ThenDuplicateOrderReference() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.putBack(
                            DROPPED_IN_FORCE,
                            NO_FACT,
                            tenToNoon(NEXT_WEEK),
                            dateWithReference(NEXT_WEEK, REFERENCE),
                            TODAY,
                            LATER),
                    new DuplicateOrderReference(NEXT_WEEK));
        }

        static Stream<Arguments> statusesThatAreNotPutBack() {
            return Stream.of(
                    Arguments.of(
                            new Placement.Planned(Attempt.FIRST, PlanStanding.IN_FORCE), NO_FACT, OrderStatus.PLANNED),
                    Arguments.of(NOT_PLACED, NO_FACT, OrderStatus.UNPLANNED),
                    Arguments.of(
                            new Placement.Planned(Attempt.FIRST, PlanStanding.IN_FORCE),
                            new StopState.Delivered(Attempt.FIRST),
                            OrderStatus.DELIVERED),
                    Arguments.of(
                            new Placement.Dropped(Attempt.FIRST, PlanStanding.CANCELLED),
                            NO_FACT,
                            OrderStatus.UNPLANNED));
        }

        @ParameterizedTest(name = "{2}")
        @MethodSource("statusesThatAreNotPutBack")
        void GivenAnOrderNeitherFailedNorDropped_WhenPutBack_ThenOnlyFailedOrDroppedOrdersArePutBack(
                Placement placement, StopState progress, OrderStatus status) {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.putBack(placement, progress, tenToNoon(NEXT_WEEK), emptyDate(NEXT_WEEK), TODAY, LATER),
                    new OnlyFailedOrDroppedOrdersArePutBack(status));
        }

        @Test
        void GivenAPastTargetDate_WhenPutBack_ThenDateInPast() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.putBack(
                            DROPPED_IN_FORCE, NO_FACT, tenToNoon(YESTERDAY), emptyDate(YESTERDAY), TODAY, LATER),
                    new DateInPast(YESTERDAY));
        }

        @Test
        void GivenAWindowOffTheTargetDate_WhenPutBack_ThenWindowNotOnDate() {
            Order order = anOrder().restored();

            assertRefusedUnchanged(
                    order,
                    () -> order.putBack(
                            DROPPED_IN_FORCE, NO_FACT, tenToNoon(TOMORROW), emptyDate(NEXT_WEEK), TODAY, LATER),
                    new WindowNotOnDate(tenToNoon(TOMORROW), NEXT_WEEK));
        }
    }

    @Nested
    class Anonymizing {

        private final DeliveryDate longAgo = DeliveryDate.of(TODAY.minusDays(Order.RETENTION_DAYS));

        @Test
        void GivenAnOrder90DaysPastItsDate_WhenAnonymized_ThenItsPersonalFieldsAreGoneAndItsFiguresStay() {
            Order order = anOrder().on(longAgo).restored();

            order.anonymize(TODAY);

            OrderSnapshot anonymized = order.snapshot();
            assertThat(anonymized.anonymized()).isTrue();
            assertThat(anonymized.recipient()).isEqualTo(Recipient.NONE);
            assertThat(anonymized.note()).isEmpty();
            assertThat(anonymized.access()).isEqualTo(AccessDetails.NONE);
            assertThat(anonymized.reference()).isEmpty();
            assertThat(anonymized.weight()).isEqualTo(kilograms("12.5"));
            assertThat(anonymized.customer()).isEqualTo(CUSTOMER);
            assertThat(order.pendingEvents()).isEmpty();
        }

        @Test
        void GivenAnOrder89DaysPastItsDate_WhenAnonymized_ThenRetentionNotOver() {
            Order order = anOrder().on(longAgo).restored();

            assertRefusedUnchanged(order, () -> order.anonymize(TODAY.minusDays(1)), new RetentionNotOver());
        }

        @Test
        void GivenAnAnonymizedOrder_WhenAnonymized_ThenOrderAnonymized() {
            Order order = anOrder().on(longAgo).anonymized().restored();

            assertRefusedUnchanged(order, () -> order.anonymize(TODAY), new OrderAnonymized(ORDER));
        }
    }

    /** Each state × each command: the command is taken, or refused with the named error. */
    @Nested
    class Lifecycle {

        private static final DeliveryDate OLD = DeliveryDate.of(TODAY.minusDays(Order.RETENTION_DAYS));

        enum OrderState {
            ACTIVE,
            CANCELLED,
            ANONYMIZED
        }

        enum Command {
            CHANGE(order -> order.change(changeOn(OLD), NO_FACT, LATER), (before, order) -> {
                assertThat(order.window()).isEqualTo(changeOn(OLD).window());
                assertThat(order.lastChangedAt()).isEqualTo(LATER);
                assertThat(order.pendingEvents()).isEmpty();
            }),
            RESCHEDULE(
                    order -> order.reschedule(tenToNoon(NEXT_WEEK), emptyDate(NEXT_WEEK), NO_FACT, TODAY, LATER),
                    (before, order) -> {
                        assertThat(order.date()).isEqualTo(NEXT_WEEK);
                        assertThat(order.pendingEvents())
                                .containsExactly(new OrderRescheduled(
                                        new EventId("OrderRescheduled", ORDER.value(), Version.of(4), 0),
                                        LATER,
                                        ORDER,
                                        OLD,
                                        NEXT_WEEK,
                                        1));
                    }),
            CANCEL(order -> order.cancel(NO_FACT, LATER), (before, order) -> {
                assertThat(order.snapshot().state()).isEqualTo(new Order.State.Cancelled(LATER));
                assertThat(order.pendingEvents())
                        .containsExactly(new OrderCancelled(
                                new EventId("OrderCancelled", ORDER.value(), Version.of(4), 0), LATER, ORDER, OLD, 1));
            }),
            PUT_BACK(
                    order -> order.putBack(
                            NOT_PLACED, FAILED_FIRST, tenToNoon(NEXT_WEEK), emptyDate(NEXT_WEEK), TODAY, LATER),
                    (before, order) -> {
                        assertThat(order.attempt()).isEqualTo(Attempt.of(2));
                        assertThat(order.date()).isEqualTo(NEXT_WEEK);
                        assertThat(order.pendingEvents()).isEmpty();
                    }),
            ANONYMIZE(order -> order.anonymize(TODAY), (before, order) -> {
                OrderSnapshot after = order.snapshot();
                assertThat(after.anonymized()).isTrue();
                assertThat(after.reference()).isEmpty();
                assertThat(after.state()).isEqualTo(before.state());
                assertThat(order.pendingEvents()).isEmpty();
            });

            private final Consumer<Order> call;
            private final BiConsumer<OrderSnapshot, Order> expected;

            Command(Consumer<Order> call, BiConsumer<OrderSnapshot, Order> expected) {
                this.call = call;
                this.expected = expected;
            }
        }

        private static Order inState(OrderState state) {
            OrderBuilder builder = anOrder().on(OLD);
            return switch (state) {
                case ACTIVE -> builder.restored();
                case CANCELLED -> builder.cancelledAt(NOW).restored();
                case ANONYMIZED -> builder.anonymized().restored();
            };
        }

        static Stream<Arguments> matrix() {
            OrdersError cancelled = new OrderAlreadyCancelled(ORDER);
            OrdersError anonymized = new OrderAnonymized(ORDER);
            return Stream.of(
                    Arguments.of(OrderState.ACTIVE, Command.CHANGE, null),
                    Arguments.of(OrderState.ACTIVE, Command.RESCHEDULE, null),
                    Arguments.of(OrderState.ACTIVE, Command.CANCEL, null),
                    Arguments.of(OrderState.ACTIVE, Command.PUT_BACK, null),
                    Arguments.of(OrderState.ACTIVE, Command.ANONYMIZE, null),
                    Arguments.of(OrderState.CANCELLED, Command.CHANGE, cancelled),
                    Arguments.of(OrderState.CANCELLED, Command.RESCHEDULE, cancelled),
                    Arguments.of(OrderState.CANCELLED, Command.CANCEL, cancelled),
                    Arguments.of(OrderState.CANCELLED, Command.PUT_BACK, cancelled),
                    Arguments.of(OrderState.CANCELLED, Command.ANONYMIZE, null),
                    Arguments.of(OrderState.ANONYMIZED, Command.CHANGE, anonymized),
                    Arguments.of(OrderState.ANONYMIZED, Command.RESCHEDULE, anonymized),
                    Arguments.of(OrderState.ANONYMIZED, Command.CANCEL, anonymized),
                    Arguments.of(OrderState.ANONYMIZED, Command.PUT_BACK, anonymized),
                    Arguments.of(OrderState.ANONYMIZED, Command.ANONYMIZE, anonymized));
        }

        @ParameterizedTest(name = "{0} × {1} → {2}")
        @MethodSource("matrix")
        void GivenAnOrderInEachState_WhenEachCommandRuns_ThenItTakesItsResultOrIsRefusedByName(
                OrderState state, Command command, @Nullable OrdersError expected) {
            Order order = inState(state);

            if (expected == null) {
                OrderSnapshot before = order.snapshot();
                command.call.accept(order);
                command.expected.accept(before, order);
            } else {
                assertRefusedUnchanged(order, () -> command.call.accept(order), expected);
            }
        }
    }

    @Nested
    class Restoring {

        static Stream<Arguments> builderStates() {
            return Stream.of(
                    Arguments.of("placed", anOrder().placed()),
                    Arguments.of("active", anOrder().restored()),
                    Arguments.of(
                            "without a reference", anOrder().withoutReference().restored()),
                    Arguments.of("put back", anOrder().atAttempt(3).restored()),
                    Arguments.of("cancelled", anOrder().cancelledAt(NOW).restored()),
                    Arguments.of("anonymized", anOrder().anonymized().restored()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("builderStates")
        void GivenAnOrderInEachBuilderState_WhenRestoredFromItsSnapshot_ThenItIsTheSame(String state, Order order) {
            assertThat(Order.restore(order.snapshot()).snapshot()).isEqualTo(order.snapshot());
        }

        @Test
        void GivenAStoredWindowOffTheOrdersDate_WhenRestored_ThenStoredStateInvalid() {
            OrderSnapshot good = anOrder().snapshot();
            OrderSnapshot damaged = new OrderSnapshot(
                    good.id(),
                    good.customer(),
                    good.date(),
                    tenToNoon(NEXT_WEEK),
                    good.weight(),
                    good.volume(),
                    good.serviceTime(),
                    good.priority(),
                    good.leaveAtDoor(),
                    good.reference(),
                    good.recipient(),
                    good.note(),
                    good.access(),
                    good.attempt(),
                    good.state(),
                    good.anonymized(),
                    good.lastChangedAt(),
                    good.version());

            assertThatThrownBy(() -> Order.restore(damaged))
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail()).isEqualTo("the window lies off the order's date"));
        }

        static Stream<Arguments> personalFieldsLeftAfterAnonymizing() {
            return Stream.of(
                    Arguments.of(
                            "a recipient",
                            OrdersTestData.RECIPIENT,
                            Optional.empty(),
                            AccessDetails.NONE,
                            Optional.empty()),
                    Arguments.of(
                            "a note",
                            Recipient.NONE,
                            Optional.of(OrdersTestData.NOTE),
                            AccessDetails.NONE,
                            Optional.empty()),
                    Arguments.of(
                            "access details",
                            Recipient.NONE,
                            Optional.empty(),
                            OrdersTestData.ACCESS,
                            Optional.empty()),
                    Arguments.of(
                            "a reference",
                            Recipient.NONE,
                            Optional.empty(),
                            AccessDetails.NONE,
                            Optional.of(REFERENCE)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("personalFieldsLeftAfterAnonymizing")
        void GivenAStoredAnonymizedOrderWithAPersonalField_WhenRestored_ThenStoredStateInvalid(
                String field,
                Recipient recipient,
                Optional<DriverNote> note,
                AccessDetails access,
                Optional<ExternalReference> reference) {
            OrderSnapshot good = anOrder().anonymized().snapshot();
            OrderSnapshot damaged = new OrderSnapshot(
                    good.id(),
                    good.customer(),
                    good.date(),
                    good.window(),
                    good.weight(),
                    good.volume(),
                    good.serviceTime(),
                    good.priority(),
                    good.leaveAtDoor(),
                    reference,
                    recipient,
                    note,
                    access,
                    good.attempt(),
                    good.state(),
                    true,
                    good.lastChangedAt(),
                    good.version());

            assertThatThrownBy(() -> Order.restore(damaged))
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail())
                                    .isEqualTo("an anonymized order holds a personal field"));
        }

        @Test
        void GivenAStoredCancellationAfterTheLastChange_WhenRestored_ThenStoredStateInvalid() {
            OrderBuilder damaged = anOrder().lastChangedAt(NOW).cancelledAt(LATER);

            assertThatThrownBy(damaged::restored)
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail())
                                    .isEqualTo("the order was cancelled after its last change"));
        }

        @Test
        void GivenAStoredAttemptOfZero_WhenRebuilt_ThenStoredStateInvalid() {
            assertThatThrownBy(() -> Attempt.of(0)).isInstanceOf(StoredStateInvalid.class);
        }
    }
}
