package com.dispatchflow.orders.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.domain.AccessDetails;
import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.Customer;
import com.dispatchflow.orders.domain.CustomerMatch;
import com.dispatchflow.orders.domain.DailyWindow;
import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.orders.domain.GeocodeResult;
import com.dispatchflow.orders.domain.InUse;
import com.dispatchflow.orders.domain.Location;
import com.dispatchflow.orders.domain.MatchQuality;
import com.dispatchflow.orders.domain.NormalizedAddress;
import com.dispatchflow.orders.domain.Order;
import com.dispatchflow.orders.domain.OrderDefaults;
import com.dispatchflow.orders.domain.OrderDetails;
import com.dispatchflow.orders.domain.Recipient;
import com.dispatchflow.orders.domain.ServiceArea;
import com.dispatchflow.orders.domain.ServiceTime;
import com.dispatchflow.orders.domain.StopState;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import com.dispatchflow.shared.application.StaleVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link CustomerRepository} against a real PostgreSQL (ADR-126, ADR-127): what a customer stores
 * and loads as, the version, the candidates at an address, the last delivery date, the customers
 * due for anonymization, and the stored states the load check and the schema refuse.
 *
 * <p>The database is shared and never cleaned, and the retention lookup reads every customer. So
 * test {@code n} keys its own customers ({@code customer(n)}, {@code customer(100 + n)}, …), gives
 * each its own address, and reads only its own customers out of a lookup's answer.
 */
@IntegrationTest
class CustomerPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6375_7374L;
    private static final Instant CREATED = israel(LocalDateTime.of(2025, 3, 27, 8, 0));
    private static final Instant LATER = israel(LocalDateTime.of(2025, 9, 1, 10, 0));
    private static final OrderDefaults DEFAULTS = new OrderDefaults(
            DailyWindow.of(LocalTime.of(9, 0), LocalTime.of(17, 0)), Optional.of(ServiceTime.ofSeconds(300)));
    private static final OrderDefaults NO_DEFAULT_SERVICE_TIME =
            new OrderDefaults(DailyWindow.of(LocalTime.of(8, 0), LocalTime.of(12, 0)), Optional.empty());

    /** A square around central Tel Aviv: longitudes 34.70 to 34.90, latitudes 32.00 to 32.20. */
    private static final ServiceArea AREA = ServiceArea.of(new GeometryFactory().createPolygon(new Coordinate[] {
        new Coordinate(34.70, 32.00),
        new Coordinate(34.90, 32.00),
        new Coordinate(34.90, 32.20),
        new Coordinate(34.70, 32.20),
        new Coordinate(34.70, 32.00)
    }));

    private static final GeoPoint INSIDE = GeoPoint.of(32.08, 34.78);
    private static final GeoPoint OUTSIDE = GeoPoint.of(31.25, 34.79);

    @Autowired
    private CustomerRepository repository;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    // --- round trips (ADR-127 decision 1)

    /** The states a customer stores and loads in, reached through commands. */
    enum Scenario {
        REGISTERED,
        WITHOUT_NOTE_OR_DEFAULT_SERVICE_TIME,
        GEOCODED_WITH_FLAGS,
        NOT_FOUND,
        PLACED_BY_HAND,
        RETIRED,
        ANONYMIZED
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenACustomer_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        Customer added = transactions.inTransaction(() -> {
            Customer customer = switch (scenario) {
                case REGISTERED -> registered(n);
                case WITHOUT_NOTE_OR_DEFAULT_SERVICE_TIME ->
                    Customer.register(
                            customer(n),
                            name(n),
                            phone(n),
                            address(n),
                            Optional.empty(),
                            NO_DEFAULT_SERVICE_TIME,
                            CREATED);
                case GEOCODED_WITH_FLAGS -> geocodedOutsideAndPartly(n);
                case NOT_FOUND -> notFound(n);
                case PLACED_BY_HAND -> placedByHand(n);
                case RETIRED -> retired(n, LATER);
                case ANONYMIZED -> anonymized(n);
            };
            repository.add(customer);
            return customer;
        });

        Customer loaded =
                transactions.inTransaction(() -> repository.get(customer(n)).orElseThrow());

        assertThat(loaded.snapshot()).isEqualTo(added.snapshot());
        assertThat(loaded.id()).isEqualTo(customer(n));
        assertThat(loaded.location()).isEqualTo(added.location());
        assertThat(loaded.flags()).containsExactlyElementsOf(added.flags());
        assertThat(loaded.state()).isEqualTo(added.state());
        assertThat(loaded.orderDefaults()).isEqualTo(added.orderDefaults());
        assertThat(loaded.normalizedAddress()).isEqualTo(added.normalizedAddress());
        assertThat(loaded.asCandidate()).isEqualTo(added.asCandidate());
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoCustomerWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(customer(8))))
                .isEmpty();
    }

    @Test
    void GivenACustomer_WhenAddedAndItsAddressChanges_ThenTheStoredNormalizedAddressFollowsIt() {
        int n = 9;
        transactions.inTransaction(() -> repository.add(registered(n)));
        String added = storedNormalizedAddress(n);

        transactions.inTransaction(() -> {
            Customer customer = repository.get(customer(n)).orElseThrow();
            customer.changeAddress(address(100 + n));
            repository.save(customer);
        });

        assertThat(added).isEqualTo(NormalizedAddress.of(address(n)).text());
        assertThat(storedNormalizedAddress(n))
                .isEqualTo(NormalizedAddress.of(address(100 + n)).text());
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedCustomer_WhenOnlyItsFlagsAndThenItsAddressChange_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 10;
        transactions.inTransaction(() -> repository.add(registered(n)));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            Customer customer = repository.get(customer(n)).orElseThrow();
            // Not found leaves the location missing, so only a flag row changes.
            customer.recordGeocode(new GeocodeResult.NotFound(NormalizedAddress.of(address(n))), AREA);
            repository.save(customer);
        });
        long flagged = storedVersion(n);
        transactions.inTransaction(() -> {
            Customer customer = repository.get(customer(n)).orElseThrow();
            customer.changeAddress(address(100 + n));
            repository.save(customer);
        });

        assertThat(List.of(added, flagged, storedVersion(n))).containsExactly(0L, 1L, 2L);
        assertThat(transactions.inTransaction(() ->
                        repository.get(customer(n)).orElseThrow().snapshot().address()))
                .contains(address(100 + n));
    }

    @Test
    void GivenTwoTransactionsOnOneCustomer_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 11;
        transactions.inTransaction(() -> repository.add(registered(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Customer mine = repository.get(customer(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Customer theirs = repository.get(customer(n)).orElseThrow();
                        theirs.changeAddress(address(100 + n));
                        repository.save(theirs);
                    });
                    mine.placeLocation(INSIDE, AREA);
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Customer");
                    assertThat(stale.id()).isEqualTo(customer(n).value());
                });

        Customer stored =
                transactions.inTransaction(() -> repository.get(customer(n)).orElseThrow());
        assertThat(stored.version()).isEqualTo(Version.of(1));
        assertThat(stored.snapshot().address()).contains(address(100 + n));
        assertThat(stored.location()).isEqualTo(new Location.Missing());
    }

    // --- the candidates at an address (FR-60, FR-310 to FR-312)

    @Test
    void GivenCustomersInEveryState_WhenTheCandidatesAtAnAddressAreRead_ThenOnlyTheActiveOnesThereAreCandidates() {
        int n = 20;
        Address typed = Address.of("רח' הנביאים " + n + ", ירושלים");
        // Typed another way, it normalizes alike (FR-398).
        Address typedAnotherWay = Address.of("רחוב הנביאים " + n + " ירושלים");
        transactions.inTransaction(() -> {
            repository.add(registeredAt(n, typed, DEFAULTS));
            repository.add(registeredAt(100 + n, typedAnotherWay, NO_DEFAULT_SERVICE_TIME));
            Customer retired = registeredAt(200 + n, typed, DEFAULTS);
            retired.retire(InUse.NONE, LATER);
            repository.add(retired);
            repository.add(registeredAt(300 + n, address(300 + n), DEFAULTS));
        });

        List<CustomerMatch.Candidate> candidates =
                transactions.inTransaction(() -> repository.candidatesAt(NormalizedAddress.of(typed)));

        assertThat(candidates)
                .containsExactlyInAnyOrder(
                        new CustomerMatch.Candidate(customer(n), name(n), phone(n), DEFAULTS),
                        new CustomerMatch.Candidate(
                                customer(100 + n), name(100 + n), phone(100 + n), NO_DEFAULT_SERVICE_TIME));
    }

    @Test
    void GivenAnAddressNoCustomerHas_WhenTheCandidatesThereAreRead_ThenThereAreNone() {
        assertThat(transactions.inTransaction(() -> repository.candidatesAt(NormalizedAddress.of(address(21)))))
                .isEmpty();
    }

    // --- the last delivery date (ADR-102)

    @Test
    void GivenACustomerWithOrdersOneOfThemCancelled_WhenItsLastDeliveryDateIsRead_ThenItIsTheLatestOfThemAll() {
        int n = 30;
        DeliveryDate first = DeliveryDate.of(LocalDate.of(2031, 2, 3));
        DeliveryDate latest = DeliveryDate.of(LocalDate.of(2031, 2, 10));
        transactions.inTransaction(() -> {
            repository.add(registered(n));
            orders.add(orderFor(n, 1, first));
            Order cancelled = orderFor(n, 2, latest);
            cancelled.cancel(new StopState.NoFact(), LATER);
            orders.add(cancelled);
            orders.add(orderFor(100 + n, 3, DeliveryDate.of(LocalDate.of(2031, 3, 1))));
        });

        assertThat(transactions.inTransaction(() -> repository.lastDeliveryDateOf(customer(n))))
                .contains(latest);
    }

    @Test
    void GivenACustomerWithNoOrder_WhenItsLastDeliveryDateIsRead_ThenThereIsNone() {
        int n = 31;
        transactions.inTransaction(() -> repository.add(registered(n)));

        assertThat(transactions.inTransaction(() -> repository.lastDeliveryDateOf(customer(n))))
                .isEmpty();
    }

    // --- the customers due for anonymization (BR-98, NFR-257, ADR-075)

    @Test
    void GivenCustomersInEveryState_WhenTheDueOnesAreListed_ThenEachIsDueOnceItsIsraelYearHasPassed() {
        int retiredDue = 40;
        int retiredADayLater = 41;
        int activeWithNoOrder = 42;
        int activeWithAnOrder = 43;
        int anonymized = 44;
        int activeCreatedLater = 45;
        // Created on 27 March 2025, still standard time; the year ends on 27 March 2026, when the
        // clock goes forward at 02:00, so Israel's midnight that day is 22:00 UTC the day before.
        Instant retiredAt = israel(LocalDateTime.of(2025, 3, 27, 12, 0));
        Instant end =
                retiredAt.atZone(IsraelDateTime.ZONE).plus(Customer.RETENTION).toInstant();
        DeliveryDate orderDate = DeliveryDate.of(LocalDate.of(2025, 4, 10));
        transactions.inTransaction(() -> {
            repository.add(retired(retiredDue, retiredAt));
            repository.add(retired(retiredADayLater, retiredAt.plusSeconds(86_400)));
            repository.add(registered(activeWithNoOrder));
            repository.add(registered(activeWithAnOrder));
            orders.add(orderFor(activeWithAnOrder, 1, orderDate));
            Customer gone = registered(anonymized);
            gone.anonymize(Optional.empty(), end);
            repository.add(gone);
            repository.add(registeredAt(activeCreatedLater, address(activeCreatedLater), DEFAULTS, LATER));
        });
        Set<CustomerId> ours = Set.of(
                customer(retiredDue),
                customer(retiredADayLater),
                customer(activeWithNoOrder),
                customer(activeWithAnOrder),
                customer(anonymized),
                customer(activeCreatedLater));

        assertThat(dueInAUtcSession(israel(LocalDateTime.of(2026, 3, 26, 23, 59, 59)), ours))
                .isEmpty();
        assertThat(dueInAUtcSession(israel(LocalDateTime.of(2026, 3, 27, 0, 0)), ours))
                .containsExactly(customer(activeWithNoOrder));
        assertThat(dueInAUtcSession(end.minusSeconds(1), ours)).containsExactly(customer(activeWithNoOrder));
        assertThat(dueInAUtcSession(end, ours))
                .containsExactlyInAnyOrder(customer(activeWithNoOrder), customer(retiredDue));
        assertThat(dueInAUtcSession(israel(LocalDateTime.of(2026, 4, 9, 23, 59, 59)), ours))
                .containsExactlyInAnyOrder(
                        customer(activeWithNoOrder), customer(retiredDue), customer(retiredADayLater));
        assertThat(dueInAUtcSession(israel(LocalDateTime.of(2026, 4, 10, 0, 0)), ours))
                .containsExactlyInAnyOrder(
                        customer(activeWithNoOrder),
                        customer(retiredDue),
                        customer(retiredADayLater),
                        customer(activeWithAnOrder));
        assertThat(end).isEqualTo(israel(LocalDateTime.of(2026, 3, 27, 12, 0)));
    }

    // --- the load check (ADR-127 decision 1), mirroring the domain's damaged snapshots

    private static final String PERSONAL_DATA = "an anonymized customer holds personal data";
    private static final String INCOMPLETE = "a customer lacks its name, phone or address";

    static Stream<Arguments> damagedRows() {
        return Stream.of(
                Arguments.of(
                        "an anonymized customer with a name",
                        1_001,
                        Base.ANONYMIZED,
                        (Damage) rows -> rows.update("name = 'דנה לוי'"),
                        PERSONAL_DATA),
                Arguments.of(
                        "an anonymized customer with a phone",
                        1_002,
                        Base.ANONYMIZED,
                        (Damage) rows -> rows.update("phone = '+97231234567'"),
                        PERSONAL_DATA),
                Arguments.of(
                        "an anonymized customer with an address",
                        1_003,
                        Base.ANONYMIZED,
                        (Damage) rows -> rows.update("address = 'הרצל 5', normalized_address = 'הרצל 5'"),
                        PERSONAL_DATA),
                Arguments.of(
                        "an anonymized customer with a note",
                        1_004,
                        Base.ANONYMIZED,
                        (Damage) rows -> rows.update("note = 'שער אחורי'"),
                        PERSONAL_DATA),
                Arguments.of(
                        "an anonymized customer with a location",
                        1_005,
                        Base.ANONYMIZED,
                        (Damage) rows -> rows.update(
                                "location_kind = 'HAND_PLACED', location_latitude = 32.08, location_longitude = 34.78"),
                        PERSONAL_DATA),
                Arguments.of(
                        "an anonymized customer with a flag",
                        1_006,
                        Base.ANONYMIZED,
                        (Damage) rows -> rows.flag("NO_MATCH"),
                        PERSONAL_DATA),
                Arguments.of(
                        "an active customer with no name",
                        1_007,
                        Base.ACTIVE,
                        (Damage) rows -> rows.update("name = NULL"),
                        INCOMPLETE),
                Arguments.of(
                        "an active customer with no phone",
                        1_008,
                        Base.ACTIVE,
                        (Damage) rows -> rows.update("phone = NULL"),
                        INCOMPLETE),
                Arguments.of(
                        "an active customer with no address",
                        1_009,
                        Base.ACTIVE,
                        (Damage) rows -> rows.update("address = NULL, normalized_address = NULL"),
                        INCOMPLETE),
                Arguments.of(
                        "a hand-placed location with a flag",
                        1_010,
                        Base.PLACED_BY_HAND,
                        (Damage) rows -> rows.flag("PARTIAL_MATCH"),
                        "a hand-placed location carries an address flag"),
                Arguments.of(
                        "a retirement before the creation",
                        1_011,
                        Base.RETIRED,
                        (Damage) rows -> rows.update("state_since = created_at - interval '1 second'"),
                        "the customer's state is older than its creation"),
                Arguments.of(
                        "a default window that ends before it starts",
                        1_012,
                        Base.ACTIVE,
                        (Damage) rows ->
                                rows.update("default_window_starts_at = default_window_ends_at + interval '1 hour'"),
                        "a stored value is refused with INVALID_DAILY_WINDOW"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredCustomerBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Base base, Damage damage, String detail) {
        transactions.inTransaction(() -> repository.add(base.build(n)));
        String subject = detail.startsWith("a stored value") ? "DailyWindow" : "Customer";

        // The damage and the load share a transaction, which the refused load rolls back.
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    damage.apply(new Rows(jdbc, n));
                    return repository.get(customer(n));
                }))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
        assertThat(transactions.inTransaction(() -> repository.get(customer(n))))
                .isPresent();
    }

    @Test
    void GivenAStoredNameTheNameRuleRefuses_WhenLoaded_ThenTheConvertersStoredStateInvalidIsTheCause() {
        int n = 1_013;
        transactions.inTransaction(() -> repository.add(registered(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    new Rows(jdbc, n).update("name = ' '");
                    return repository.get(customer(n));
                }))
                .satisfies(thrown -> assertThat(StoredStates.invalidIn(thrown))
                        .hasValueSatisfying(
                                invalid -> assertThat(invalid.subject()).isEqualTo("ContactName")));
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("an address without its normalized form", 1_101, Base.ACTIVE, (Damage)
                        rows -> rows.update("normalized_address = NULL")),
                Arguments.of("a hand-placed location with no point", 1_102, Base.ACTIVE, (Damage)
                        rows -> rows.update("location_kind = 'HAND_PLACED'")),
                Arguments.of("half a point", 1_103, Base.PLACED_BY_HAND, (Damage)
                        rows -> rows.update("location_longitude = NULL")),
                Arguments.of("a geocoded location with no match quality", 1_104, Base.GEOCODED, (Damage)
                        rows -> rows.update("match_quality = NULL")),
                Arguments.of("a hand-placed location with a match quality", 1_105, Base.PLACED_BY_HAND, (Damage)
                        rows -> rows.update("match_quality = 'EXACT'")),
                Arguments.of("an active customer with a state time", 1_106, Base.ACTIVE, (Damage)
                        rows -> rows.update("state_since = created_at")),
                Arguments.of("a retired customer with no retirement time", 1_107, Base.RETIRED, (Damage)
                        rows -> rows.update("state_since = NULL")),
                Arguments.of(
                        "an unknown state", 1_108, Base.ACTIVE, (Damage) rows -> rows.update("state = 'SUSPENDED'")),
                Arguments.of("an unknown flag", 1_109, Base.ACTIVE, (Damage) rows -> rows.flag("SUSPICIOUS")),
                Arguments.of("an unknown match quality", 1_110, Base.GEOCODED, (Damage)
                        rows -> rows.update("match_quality = 'CLOSE'")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredCustomer_WhenTheRowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Base base, Damage damage) {
        transactions.inTransaction(() -> repository.add(base.build(n)));

        assertThatThrownBy(() -> damage.apply(new Rows(jdbc, n))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(customer(n))))
                .isPresent();
    }

    /** The valid states the damage cases start from. */
    enum Base {
        ACTIVE,
        GEOCODED,
        PLACED_BY_HAND,
        RETIRED,
        ANONYMIZED;

        Customer build(int n) {
            return switch (this) {
                case ACTIVE -> registered(n);
                case GEOCODED -> geocodedOutsideAndPartly(n);
                case PLACED_BY_HAND -> placedByHand(n);
                case RETIRED -> retired(n, LATER);
                case ANONYMIZED -> anonymized(n);
            };
        }
    }

    /** One damage to customer {@code n}'s stored rows. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** Writes to customer {@code n}'s rows; the SQL is the test's own constant text. */
    record Rows(JdbcClient jdbc, int n) {

        void update(String assignments) {
            jdbc.sql("UPDATE orders.customer SET " + assignments + " WHERE id = ?")
                    .param(customer(n).value())
                    .update();
        }

        void flag(String flag) {
            jdbc.sql("INSERT INTO orders.customer_address_flag (customer_id, flag) VALUES (?, ?)")
                    .params(customer(n).value(), flag)
                    .update();
        }
    }

    // --- states, reached through the domain's commands

    private static Customer registered(int n) {
        return registeredAt(n, address(n), DEFAULTS);
    }

    private static Customer registeredAt(int n, Address address, OrderDefaults defaults) {
        return registeredAt(n, address, defaults, CREATED);
    }

    private static Customer registeredAt(int n, Address address, OrderDefaults defaults, Instant createdAt) {
        return Customer.register(
                customer(n), name(n), phone(n), address, DriverNote.of("הכניסה מהחצר " + n), defaults, createdAt);
    }

    /** Geocoded outside the service area with a partial match, so it carries two flags. */
    private static Customer geocodedOutsideAndPartly(int n) {
        Customer customer = registered(n);
        customer.recordGeocode(
                new GeocodeResult.Found(NormalizedAddress.of(address(n)), OUTSIDE, MatchQuality.PARTIAL, 12.5), AREA);
        return customer;
    }

    private static Customer notFound(int n) {
        Customer customer = registered(n);
        customer.recordGeocode(new GeocodeResult.NotFound(NormalizedAddress.of(address(n))), AREA);
        return customer;
    }

    private static Customer placedByHand(int n) {
        Customer customer = notFound(n);
        customer.placeLocation(INSIDE, AREA);
        return customer;
    }

    private static Customer retired(int n, Instant at) {
        Customer customer = geocodedOutsideAndPartly(n);
        customer.retire(InUse.NONE, at);
        return customer;
    }

    /** Retired at {@link #LATER}, and anonymized when its retention ends. */
    private static Customer anonymized(int n) {
        Customer customer = retired(n, LATER);
        customer.anonymize(
                Optional.empty(),
                LATER.atZone(IsraelDateTime.ZONE).plus(Customer.RETENTION).toInstant());
        return customer;
    }

    /** An order of customer {@code n} on a date, placed on that date. */
    private Order orderFor(int n, int o, DeliveryDate date) {
        OrderDetails details = new OrderDetails(
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
        return Order.place(
                OrderId.of(new UUID(KEY_PREFIX + 1, 100L * n + o)),
                customer(n),
                details,
                DEFAULTS,
                orders.capacityUnderLock(date),
                date.value(),
                CREATED);
    }

    // --- keys, values and stored rows

    private static CustomerId customer(int n) {
        return CustomerId.of(new UUID(KEY_PREFIX, n));
    }

    private static ContactName name(int n) {
        return ContactName.of("Customer " + n);
    }

    /** An Israeli mobile number of its own for each {@code n}. */
    private static PhoneNumber phone(int n) {
        return PhoneNumber.ofStored("+97252%07d".formatted(5_000_000 + n));
    }

    private static Address address(int n) {
        return Address.of("הרצל " + n + ", תל אביב");
    }

    private static Instant israel(LocalDateTime local) {
        return local.atZone(IsraelDateTime.ZONE).toInstant();
    }

    /**
     * Lists the due customers in a session whose zone is UTC, not the JVM's, so the answer shows
     * the SQL keeps Israel's calendar whatever the session's zone; only this test's customers.
     */
    private List<CustomerId> dueInAUtcSession(Instant now, Set<CustomerId> ours) {
        return transactions.inTransaction(() -> {
            jdbc.sql("SET LOCAL TimeZone = 'UTC'").update();
            return repository.dueForAnonymization(now).stream()
                    .filter(ours::contains)
                    .toList();
        });
    }

    private long storedVersion(int n) {
        return jdbc.sql("SELECT version FROM orders.customer WHERE id = ?")
                .param(customer(n).value())
                .query(Long.class)
                .single();
    }

    private String storedNormalizedAddress(int n) {
        return jdbc.sql("SELECT normalized_address FROM orders.customer WHERE id = ?")
                .param(customer(n).value())
                .query(String.class)
                .single();
    }
}
