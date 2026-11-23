package com.dispatchflow.orders.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.domain.AccessDetails;
import com.dispatchflow.orders.domain.CapacityDescription;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.DailyWindow;
import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.orders.domain.ExternalReference;
import com.dispatchflow.orders.domain.Order;
import com.dispatchflow.orders.domain.OrderChange;
import com.dispatchflow.orders.domain.OrderDefaults;
import com.dispatchflow.orders.domain.OrderDescription;
import com.dispatchflow.orders.domain.OrderDetails;
import com.dispatchflow.orders.domain.Placement;
import com.dispatchflow.orders.domain.Recipient;
import com.dispatchflow.orders.domain.ServiceTime;
import com.dispatchflow.orders.domain.StopState;
import com.dispatchflow.orders.events.OrderCancelled;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import com.dispatchflow.shared.application.StaleVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link OrderRepository} against a real PostgreSQL (ADR-126, ADR-127): what an order stores and
 * loads as, the version and its events, a date's capacity and its lock, the orders due for
 * anonymization, a date's uncancelled orders, a customer's orders not yet anonymized, and the
 * stored states the load check and the schema refuse.
 *
 * <p>The database is shared and never cleaned, and the retention lookup reads every order. So test
 * {@code n} keys its own orders and customers ({@code order(n, o)}, {@code customer(n)}), works on
 * its own delivery date, {@code dateOf(n)}, whose neighbours no test uses, and reads only its own
 * orders out of a lookup's answer.
 */
@IntegrationTest
class OrderPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6f72_6472L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2032, 1, 5);
    private static final int DAYS_APART = 3;
    private static final OrderDefaults DEFAULTS = new OrderDefaults(
            DailyWindow.of(LocalTime.of(9, 0), LocalTime.of(17, 0)), Optional.of(ServiceTime.ofSeconds(300)));
    private static final ExternalReference REFERENCE =
            ExternalReference.of("SHOP-1001").orElseThrow();
    private static final Recipient RECIPIENT =
            new Recipient(Optional.of(ContactName.of("דנה לוי")), Optional.of(PhoneNumber.ofStored("+972523456789")));
    private static final AccessDetails ACCESS = AccessDetails.of("3", "12", "1234#");
    private static final DriverNote NOTE = DriverNote.of("לא לצלצל").orElseThrow();
    private static final StopState NO_FACT = new StopState.NoFact();

    @Autowired
    private OrderRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedEvents events;

    @BeforeEach
    void forgetEarlierEvents() {
        events.clear();
    }

    // --- round trips (ADR-127 decision 1)

    /** The states an order stores and loads in, reached through commands. */
    enum Scenario {
        PLACED,
        PLACED_BARE,
        CHANGED,
        RESCHEDULED,
        PUT_BACK,
        CANCELLED,
        ANONYMIZED
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenAnOrder_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        Order added = transactions.inTransaction(() -> {
            Order order = switch (scenario) {
                case PLACED -> placed(n, 1);
                case PLACED_BARE -> placedWith(n, 1, dateOf(n), bare());
                case CHANGED -> changed(n);
                case RESCHEDULED -> rescheduled(n);
                case PUT_BACK -> putBack(n);
                case CANCELLED -> cancelled(n, 1);
                case ANONYMIZED -> anonymized(n, 1);
            };
            repository.add(order);
            return order;
        });

        Order loaded =
                transactions.inTransaction(() -> repository.get(order(n, 1)).orElseThrow());

        assertThat(OrderDescription.of(loaded)).isEqualTo(OrderDescription.of(added));
        assertThat(loaded.id()).isEqualTo(order(n, 1));
        assertThat(loaded.date()).isEqualTo(added.date());
        assertThat(loaded.window()).isEqualTo(added.window());
        assertThat(loaded.attempt()).isEqualTo(added.attempt());
        assertThat(loaded.isCancelled()).isEqualTo(added.isCancelled());
        assertThat(loaded.reference()).isEqualTo(added.reference());
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoOrderWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(order(8, 1))))
                .isEmpty();
    }

    // --- versions, events and conflicts

    @Test
    void GivenAnAddedOrder_WhenItChangesAndIsThenCancelled_ThenTheVersionIsZeroThenOneThenTwoAndTheEventCarriesIt() {
        int n = 9;
        transactions.inTransaction(() -> repository.add(placed(n, 1)));
        long added = storedVersion(n, 1);

        transactions.inTransaction(() -> {
            Order order = repository.get(order(n, 1)).orElseThrow();
            order.change(change(dateOf(n)), NO_FACT, at(n, 1));
            repository.save(order);
        });
        long changed = storedVersion(n, 1);
        transactions.inTransaction(() -> {
            Order order = repository.get(order(n, 1)).orElseThrow();
            order.cancel(NO_FACT, at(n, 2));
            repository.save(order);
        });

        assertThat(List.of(added, changed, storedVersion(n, 1))).containsExactly(0L, 1L, 2L);
        assertThat(events.committed())
                .containsExactly(new OrderCancelled(
                        new EventId("OrderCancelled", order(n, 1).value(), Version.of(2), 0),
                        at(n, 2),
                        order(n, 1),
                        dateOf(n),
                        1));
    }

    @Test
    void GivenTwoTransactionsOnOneOrder_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 10;
        transactions.inTransaction(() -> repository.add(placed(n, 1)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Order mine = repository.get(order(n, 1)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Order theirs = repository.get(order(n, 1)).orElseThrow();
                        theirs.change(change(dateOf(n)), NO_FACT, at(n, 1));
                        repository.save(theirs);
                    });
                    mine.cancel(NO_FACT, at(n, 2));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Order");
                    assertThat(stale.id()).isEqualTo(order(n, 1).value());
                });

        Order stored =
                transactions.inTransaction(() -> repository.get(order(n, 1)).orElseThrow());
        assertThat(stored.version()).isEqualTo(Version.of(1));
        assertThat(stored.isCancelled()).isFalse();
        assertThat(stored.lastChangedAt()).isEqualTo(at(n, 1));
    }

    // --- a date's capacity under its lock (BR-101, BR-113, FR-211)

    @Test
    void GivenADatesOrdersOneOfThemCancelled_WhenItsCapacityIsRead_ThenItCountsTheUncancelledAndTheirReferences() {
        int n = 20;
        ExternalReference cancelledReference =
                ExternalReference.of("SHOP-CANCELLED").orElseThrow();
        ExternalReference otherDatesReference =
                ExternalReference.of("SHOP-ELSEWHERE").orElseThrow();
        transactions.inTransaction(() -> {
            repository.add(placed(n, 1));
            repository.add(placedWith(n, 2, dateOf(n), bare()));
            Order cancelled = placedWith(n, 3, dateOf(n), withReference(cancelledReference));
            cancelled.cancel(NO_FACT, at(n, 1));
            repository.add(cancelled);
            repository.add(placedWith(n, 4, dateOf(n).value().plusDays(1), withReference(otherDatesReference)));
        });

        CapacityDescription capacity = transactions.inTransaction(() -> CapacityDescription.of(
                repository.capacityUnderLock(dateOf(n)),
                List.of(order(n, 1), order(n, 2), order(n, 3), order(n, 4)),
                List.of(REFERENCE, cancelledReference, otherDatesReference)));

        assertThat(capacity)
                .isEqualTo(new CapacityDescription(dateOf(n), 2, Set.of(order(n, 1), order(n, 2)), Set.of(REFERENCE)));
    }

    @Test
    void GivenADateWithNoOrder_WhenItsCapacityIsRead_ThenItCountsNone() {
        int n = 21;

        assertThat(transactions.inTransaction(
                        () -> CapacityDescription.of(repository.capacityUnderLock(dateOf(n)), List.of(), List.of())))
                .isEqualTo(new CapacityDescription(dateOf(n), 0, Set.of(), Set.of()));
    }

    @Test
    void GivenATransactionHoldingADatesCapacity_WhenAnotherReadsIt_ThenItCannotTakeTheDatesLockButAnotherDatesIsFree() {
        int n = 22;

        transactions.inTransaction(() -> {
            repository.capacityUnderLock(dateOf(n));
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        shortLockTimeout();
                        repository.capacityUnderLock(dateOf(n));
                    }))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            transactions.inANewTransaction(() -> {
                shortLockTimeout();
                assertThat(repository
                                .capacityUnderLock(
                                        DeliveryDate.of(dateOf(n).value().plusDays(1)))
                                .date())
                        .isEqualTo(DeliveryDate.of(dateOf(n).value().plusDays(1)));
            });
        });

        // The lock went with the transaction that held it.
        transactions.inTransaction(() -> {
            shortLockTimeout();
            assertThat(repository.capacityUnderLock(dateOf(n)).date()).isEqualTo(dateOf(n));
        });
    }

    // --- the orders due for anonymization (NFR-257, ADR-075)

    @Test
    void
            GivenOrdersDatedAroundNinetyDaysAgo_WhenTheDueOnesAreListed_ThenThoseNinetyDaysOldOrMoreAndNotAnonymizedAreDue() {
        int n = 30;
        LocalDate today = dateOf(n).value();
        transactions.inTransaction(() -> {
            repository.add(placedWith(n, 1, today.minusDays(Order.RETENTION_DAYS), bare()));
            repository.add(placedWith(n, 2, today.minusDays(Order.RETENTION_DAYS - 1), bare()));
            Order cancelled = placedWith(n, 3, today.minusDays(Order.RETENTION_DAYS + 5), bare());
            cancelled.cancel(NO_FACT, at(n, 1));
            repository.add(cancelled);
            Order anonymized = placedWith(n, 4, today.minusDays(Order.RETENTION_DAYS + 10), bare());
            anonymized.anonymize(today);
            repository.add(anonymized);
        });
        Set<OrderId> ours = Set.of(order(n, 1), order(n, 2), order(n, 3), order(n, 4));

        List<OrderId> due = transactions.inTransaction(() -> repository.dueForAnonymization(today));

        assertThat(due).filteredOn(ours::contains).containsExactlyInAnyOrder(order(n, 1), order(n, 3));
    }

    // --- a date's uncancelled orders (FR-62)

    @Test
    void GivenADatesOrdersOneOfThemCancelled_WhenItsUncancelledOrdersAreRead_ThenTheOthersComeWhole() {
        int n = 40;
        List<Order> added = transactions.inTransaction(() -> {
            Order first = placed(n, 1);
            Order second = placedWith(n, 2, dateOf(n), bare());
            Order cancelled = cancelled(n, 3);
            repository.add(first);
            repository.add(second);
            repository.add(cancelled);
            repository.add(placedWith(n, 4, dateOf(n).value().plusDays(1), bare()));
            return List.of(first, second);
        });

        List<Order> uncancelled = transactions.inTransaction(() -> repository.uncancelledOn(dateOf(n)));

        assertThat(uncancelled.stream().map(OrderDescription::of))
                .containsExactlyInAnyOrderElementsOf(
                        added.stream().map(OrderDescription::of).toList());
    }

    @Test
    void GivenADateWithNoOrder_WhenItsUncancelledOrdersAreRead_ThenThereAreNone() {
        assertThat(transactions.inTransaction(() -> repository.uncancelledOn(dateOf(41))))
                .isEmpty();
    }

    // --- a customer's orders not yet anonymized (BR-43)

    @Test
    void GivenACustomersOrdersInEveryState_WhenThoseNotAnonymizedAreRead_ThenTheCancelledOnesAreAmongThem() {
        int n = 50;
        transactions.inTransaction(() -> {
            // Only order 1 carries the reference: a date's orders never share one (FR-211).
            repository.add(placed(n, 1));
            Order cancelled = placedWith(n, 2, dateOf(n), bare());
            cancelled.cancel(NO_FACT, at(n, 1));
            repository.add(cancelled);
            Order anonymized = placedWith(n, 3, dateOf(n), bare());
            anonymized.anonymize(dateOf(n).value().plusDays(Order.RETENTION_DAYS));
            repository.add(anonymized);
            repository.add(placedFor(100 + n, n, 4, dateOf(n), bare()));
        });

        assertThat(transactions.inTransaction(() -> repository.notAnonymizedOf(customer(n))))
                .containsExactlyInAnyOrder(order(n, 1), order(n, 2));
        assertThat(transactions.inTransaction(() -> repository.notAnonymizedOf(customer(200 + n))))
                .isEmpty();
    }

    // --- the load check (ADR-127 decision 1): the damaged stored states it refuses

    static Stream<Arguments> damagedRows() {
        return Stream.of(
                Arguments.of(
                        "a window off the order's date",
                        1_001,
                        Base.PLACED,
                        "starts_at = starts_at + interval '7 days', ends_at = ends_at + interval '7 days'",
                        "the window lies off the order's date"),
                Arguments.of(
                        "a cancellation after the last change",
                        1_006,
                        Base.CANCELLED,
                        "cancelled_at = last_changed_at + interval '1 second'",
                        "the order was cancelled after its last change"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredOrderBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Base base, String damage, String detail) {
        transactions.inTransaction(() -> repository.add(build(base, n)));

        // The damage and the load share a transaction, which the refused load rolls back.
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    damage(n, damage);
                    return repository.get(order(n, 1));
                }))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("Order");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
        assertThat(transactions.inTransaction(() -> repository.get(order(n, 1))))
                .isPresent();
    }

    @Test
    void GivenAStoredReferenceTheRuleRefuses_WhenLoaded_ThenTheConvertersStoredStateInvalidIsTheCause() {
        int n = 1_007;
        transactions.inTransaction(() -> repository.add(placed(n, 1)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    damage(n, "reference = ' '");
                    return repository.get(order(n, 1));
                }))
                .satisfies(thrown -> assertThat(StoredStates.invalidIn(thrown))
                        .hasValueSatisfying(
                                invalid -> assertThat(invalid.subject()).isEqualTo("ExternalReference")));
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("an active order with a cancellation time", 1_101, Base.PLACED, "cancelled_at = now()"),
                Arguments.of(
                        "a cancelled order with no cancellation time", 1_102, Base.CANCELLED, "cancelled_at = NULL"),
                Arguments.of("an unknown state", 1_103, Base.PLACED, "state = 'HELD'"),
                Arguments.of("an order with no window", 1_104, Base.PLACED, "starts_at = NULL"),
                Arguments.of(
                        "an anonymized order with a recipient",
                        1_002,
                        Base.ANONYMIZED,
                        "recipient_name = 'דנה לוי', recipient_phone = '+972523456789'"),
                Arguments.of("an anonymized order with a note", 1_003, Base.ANONYMIZED, "note = 'לא לצלצל'"),
                Arguments.of(
                        "an anonymized order with access details",
                        1_004,
                        Base.ANONYMIZED,
                        "floor = '3', apartment = '12', entrance_code = '1234#'"),
                Arguments.of(
                        "an anonymized order with a reference", 1_005, Base.ANONYMIZED, "reference = 'SHOP-1001'"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredOrder_WhenTheRowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Base base, String damage) {
        transactions.inTransaction(() -> repository.add(build(base, n)));

        assertThatThrownBy(() -> damage(n, damage)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(order(n, 1))))
                .isPresent();
    }

    @Test
    void GivenTwoUncancelledOrdersOnADate_WhenOneIsGivenTheOthersReference_ThenTheDatabaseRefusesTheWrite() {
        int n = 1_105;
        transactions.inTransaction(() -> {
            repository.add(placedWith(n, 1, dateOf(n), bare()));
            repository.add(placed(n, 2));
        });

        assertThatThrownBy(() -> damage(n, "reference = 'SHOP-1001'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(
                        () -> repository.get(order(n, 1)).orElseThrow().reference()))
                .isEmpty();
    }

    /**
     * FR-211 counts only a date's uncancelled orders (ADR-102), so the schema's backstop lets an
     * order take a reference that a cancelled order of its date, or an order of another date, holds.
     */
    @Test
    void GivenAReferenceHeldByACancelledOrderAndByAnotherDatesOrder_WhenAnOrderOnTheDateTakesIt_ThenItIsStored() {
        int n = 60;
        DeliveryDate nextDay = DeliveryDate.of(dateOf(n).value().plusDays(1));
        transactions.inTransaction(() -> {
            repository.add(cancelled(n, 1));
            repository.add(placedWith(n, 2, nextDay, full(nextDay)));
        });

        transactions.inTransaction(() -> repository.add(placed(n, 3)));

        assertThat(transactions.inTransaction(
                        () -> repository.get(order(n, 3)).orElseThrow().reference()))
                .contains(REFERENCE);
    }

    /** The valid states the damage cases start from, each order {@code order(n, 1)}. */
    enum Base {
        PLACED,
        CANCELLED,
        ANONYMIZED
    }

    // --- states, reached through the domain's commands

    /** Builds a damage case's base order, {@code order(n, 1)}. */
    private Order build(Base base, int n) {
        return switch (base) {
            case PLACED -> placed(n, 1);
            case CANCELLED -> cancelled(n, 1);
            case ANONYMIZED -> anonymized(n, 1);
        };
    }

    /** Placed on {@code dateOf(n)} with every optional part. */
    private Order placed(int n, int o) {
        return placedWith(n, o, dateOf(n), full(dateOf(n)));
    }

    private Order placedWith(int n, int o, LocalDate date, OrderDetails details) {
        return placedWith(n, o, DeliveryDate.of(date), details);
    }

    private Order placedWith(int n, int o, DeliveryDate date, OrderDetails details) {
        return placedFor(n, n, o, date, details);
    }

    /** Order {@code o} of test {@code n}, for customer {@code c}, placed on its date that day. */
    private Order placedFor(int c, int n, int o, DeliveryDate date, OrderDetails details) {
        return Order.place(
                order(n, o),
                customer(c),
                details,
                DEFAULTS,
                repository.capacityUnderLock(date),
                date.value(),
                at(n, 0));
    }

    private Order changed(int n) {
        Order order = placed(n, 1);
        order.change(change(dateOf(n)), NO_FACT, at(n, 1));
        return order;
    }

    private Order rescheduled(int n) {
        Order order = placed(n, 1);
        DeliveryDate next = DeliveryDate.of(dateOf(n).value().plusDays(1));
        order.reschedule(
                tenToNoon(next),
                repository.capacityUnderLock(next),
                NO_FACT,
                dateOf(n).value(),
                at(n, 1));
        return order;
    }

    /** Dropped from its plan, and put back onto the next day as its second attempt. */
    private Order putBack(int n) {
        Order order = placed(n, 1);
        DeliveryDate next = DeliveryDate.of(dateOf(n).value().plusDays(1));
        order.putBack(
                new Placement.Dropped(Attempt.FIRST, Placement.PlanStanding.IN_FORCE),
                NO_FACT,
                tenToNoon(next),
                repository.capacityUnderLock(next),
                dateOf(n).value(),
                at(n, 1));
        return order;
    }

    private Order cancelled(int n, int o) {
        Order order = placedWith(n, o, dateOf(n), full(dateOf(n)));
        order.cancel(NO_FACT, at(n, 1));
        return order;
    }

    /** Placed with every personal part, and anonymized when its 90 days are over. */
    private Order anonymized(int n, int o) {
        Order order = placedWith(n, o, dateOf(n), full(dateOf(n)));
        order.anonymize(dateOf(n).value().plusDays(Order.RETENTION_DAYS));
        return order;
    }

    private static OrderDetails full(DeliveryDate date) {
        return new OrderDetails(
                Optional.of(tenToNoon(date)),
                Weight.kilograms(new BigDecimal("12.5")),
                Volume.litres(new BigDecimal("40")),
                Optional.of(ServiceTime.ofSeconds(180)),
                Priority.of(2),
                true,
                Optional.of(REFERENCE),
                RECIPIENT,
                Optional.of(NOTE),
                ACCESS);
    }

    /** No window, service time, reference, recipient, note or access details: the defaults decide. */
    private static OrderDetails bare() {
        return new OrderDetails(
                Optional.empty(),
                Weight.kilograms(BigDecimal.ONE),
                Volume.litres(BigDecimal.ONE),
                Optional.empty(),
                Priority.byDefault(),
                false,
                Optional.empty(),
                Recipient.NONE,
                Optional.empty(),
                AccessDetails.NONE);
    }

    private static OrderDetails withReference(ExternalReference reference) {
        OrderDetails base = bare();
        return new OrderDetails(
                base.window(),
                base.weight(),
                base.volume(),
                base.serviceTime(),
                base.priority(),
                base.leaveAtDoor(),
                Optional.of(reference),
                base.recipient(),
                base.note(),
                base.access());
    }

    private static OrderChange change(DeliveryDate date) {
        return new OrderChange(
                TimeWindow.on(date, LocalTime.of(14, 0), LocalTime.of(16, 0)),
                Weight.kilograms(new BigDecimal("3.25")),
                Volume.litres(new BigDecimal("8")),
                ServiceTime.ofSeconds(600),
                Priority.of(1),
                false,
                new Recipient(Optional.empty(), Optional.of(PhoneNumber.ofStored("+972524444444"))),
                Optional.empty(),
                AccessDetails.of("", "7", ""));
    }

    private static TimeWindow tenToNoon(DeliveryDate date) {
        return TimeWindow.on(date, LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    // --- keys, dates and stored rows

    private static OrderId order(int n, int o) {
        return OrderId.of(new UUID(KEY_PREFIX, 100L * n + o));
    }

    private static CustomerId customer(int c) {
        return CustomerId.of(new UUID(KEY_PREFIX + 1, c));
    }

    private static DeliveryDate dateOf(int n) {
        return DeliveryDate.of(FIRST_DAY.plusDays((long) DAYS_APART * n));
    }

    /** {@code minutes} after 08:00 in Israel on test {@code n}'s date. */
    private static Instant at(int n, int minutes) {
        return IsraelDateTime.of(dateOf(n), LocalTime.of(8, 0)).toInstant().plusSeconds(60L * minutes);
    }

    private void shortLockTimeout() {
        jdbc.sql("SET LOCAL lock_timeout = '200ms'").update();
    }

    /** Sets columns of order {@code (n, 1)}'s row; the assignments are the test's own constant SQL. */
    private void damage(int n, String assignments) {
        jdbc.sql("UPDATE orders.customer_order SET " + assignments + " WHERE id = ?")
                .param(order(n, 1).value())
                .update();
    }

    private long storedVersion(int n, int o) {
        return jdbc.sql("SELECT version FROM orders.customer_order WHERE id = ?")
                .param(order(n, o).value())
                .query(Long.class)
                .single();
    }
}
