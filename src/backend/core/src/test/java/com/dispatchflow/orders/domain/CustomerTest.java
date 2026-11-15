package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.CustomerBuilder.ADDRESS;
import static com.dispatchflow.orders.domain.CustomerBuilder.ALSO_INSIDE;
import static com.dispatchflow.orders.domain.CustomerBuilder.AREA;
import static com.dispatchflow.orders.domain.CustomerBuilder.CREATED;
import static com.dispatchflow.orders.domain.CustomerBuilder.INSIDE;
import static com.dispatchflow.orders.domain.CustomerBuilder.OUTSIDE;
import static com.dispatchflow.orders.domain.CustomerBuilder.aCustomer;
import static com.dispatchflow.orders.domain.OrdersTestData.CUSTOMER;
import static com.dispatchflow.orders.domain.OrdersTestData.DEFAULTS;
import static com.dispatchflow.orders.domain.OrdersTestData.NOW;
import static com.dispatchflow.orders.domain.OrdersTestData.NO_DEFAULT_SERVICE_TIME;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.orders.domain.OrdersError.CustomerAnonymized;
import com.dispatchflow.orders.domain.OrdersError.CustomerInUse;
import com.dispatchflow.orders.domain.OrdersError.CustomerRetired;
import com.dispatchflow.orders.domain.OrdersError.OutsideServiceArea;
import com.dispatchflow.orders.domain.OrdersError.RetentionNotOver;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link Customer}'s invariants (DL §4.3), its REQ-wins rows (BR-104, FR-217, FR-08, BR-124) and ADR-102. */
class CustomerTest {

    /** 1 June 2026 at 09:00 in Israel, a year and two weeks before {@link OrdersTestData#NOW}. */
    private static final Instant LAST_YEAR = Instant.parse("2026-06-01T06:00:00Z");

    private static final NormalizedAddress LOOKED_UP = NormalizedAddress.of(ADDRESS);

    private static void assertRefusedUnchanged(Customer customer, ThrowingCallable call, DomainError expected) {
        CustomerSnapshot before = customer.snapshot();
        assertRefused(call, expected);
        assertThat(customer.snapshot()).isEqualTo(before);
        assertThat(customer.pendingEvents()).isEmpty();
    }

    private static GeocodeResult found(MatchQuality quality, double metresToRoad) {
        return new GeocodeResult.Found(LOOKED_UP, INSIDE, quality, metresToRoad);
    }

    private static CustomerSnapshot copy(CustomerSnapshot good, UnaryOperator<Parts> change) {
        Parts parts = change.apply(
                new Parts(good.name(), good.phone(), good.address(), good.note(), good.location(), good.flags()));
        return new CustomerSnapshot(
                good.id(),
                parts.name(),
                parts.phone(),
                parts.address(),
                parts.note(),
                parts.location(),
                parts.flags(),
                good.defaults(),
                good.state(),
                good.createdAt(),
                good.version());
    }

    /** The personal parts of a snapshot, which the restore cases change one at a time. */
    record Parts(
            Optional<ContactName> name,
            Optional<PhoneNumber> phone,
            Optional<Address> address,
            Optional<DriverNote> note,
            Location location,
            Set<AddressFlag> flags) {}

    @Nested
    class Registering {

        @Test
        void GivenItsDetailsAndTheCompanysDefaults_WhenRegistered_ThenItIsActiveWithNoLocationAndNoFlag() {
            Customer customer = aCustomer().registered();

            CustomerSnapshot registered = customer.snapshot();
            assertThat(registered.state()).isEqualTo(new Customer.State.Active());
            assertThat(registered.location()).isEqualTo(new Location.Missing());
            assertThat(registered.flags()).isEmpty();
            assertThat(registered.note()).contains(CustomerBuilder.NOTES);
            assertThat(registered.createdAt()).isEqualTo(CREATED);
            assertThat(registered.version()).isEqualTo(Version.NEW);
            assertThat(customer.orderDefaults()).isEqualTo(DEFAULTS);
            assertThat(customer.normalizedAddress()).contains(LOOKED_UP);
            assertThat(customer.pendingEvents()).isEmpty();
        }

        @Test
        void GivenNoDefaultServiceTime_WhenRegistered_ThenItsOrdersHaveNoDefaultOne() {
            Customer customer = Customer.register(
                    CUSTOMER,
                    CustomerBuilder.NAME,
                    CustomerBuilder.PHONE,
                    ADDRESS,
                    Optional.empty(),
                    NO_DEFAULT_SERVICE_TIME,
                    CREATED);

            assertThat(customer.orderDefaults().serviceTime()).isEmpty();
        }
    }

    @Nested
    class ChangingTheAddress {

        @Test
        void GivenAHandPlacedLocation_WhenTheAddressChanges_ThenTheLocationIsClearedAndGeocodingIsDue() {
            Customer customer = aCustomer().placedByHandAt(INSIDE).restored();

            boolean geocodeAgain = customer.changeAddress(Address.of("שד' רוטשילד 1"));

            assertThat(geocodeAgain).isTrue();
            assertThat(customer.location()).isEqualTo(new Location.Missing());
            assertThat(customer.normalizedAddress()).contains(NormalizedAddress.of(Address.of("שדרות רוטשילד 1")));
        }

        @Test
        void GivenFlags_WhenTheAddressChanges_ThenTheyAreCleared() {
            Customer customer = aCustomer()
                    .geocodedAt(INSIDE, AddressFlag.PARTIAL_MATCH, AddressFlag.FAR_FROM_ROAD)
                    .restored();

            customer.changeAddress(Address.of("שד' רוטשילד 1"));

            assertThat(customer.flags()).isEmpty();
        }

        @Test
        void GivenTheSameAddress_WhenChanged_ThenNothingChanges() {
            Customer customer = aCustomer().placedByHandAt(INSIDE).restored();
            CustomerSnapshot before = customer.snapshot();

            assertThat(customer.changeAddress(ADDRESS)).isFalse();
            assertThat(customer.snapshot()).isEqualTo(before);
        }
    }

    @Nested
    class RecordingAGeocode {

        @Test
        void GivenAnExactMatchInsideTheAreaNearARoad_WhenRecorded_ThenItIsTheLocationWithNoFlag() {
            Customer customer = aCustomer().restored();

            assertThat(customer.recordGeocode(found(MatchQuality.EXACT, 12), AREA))
                    .isTrue();

            assertThat(customer.location()).isEqualTo(new Location.Geocoded(INSIDE, MatchQuality.EXACT));
            assertThat(customer.flags()).isEmpty();
        }

        static Stream<Arguments> flaggedResults() {
            return Stream.of(
                    Arguments.of(found(MatchQuality.PARTIAL, 0), AddressFlag.PARTIAL_MATCH),
                    Arguments.of(found(MatchQuality.AMBIGUOUS, 0), AddressFlag.AMBIGUOUS_MATCH),
                    Arguments.of(
                            new GeocodeResult.Found(LOOKED_UP, OUTSIDE, MatchQuality.EXACT, 0),
                            AddressFlag.OUTSIDE_SERVICE_AREA),
                    Arguments.of(found(MatchQuality.EXACT, 300.5), AddressFlag.FAR_FROM_ROAD),
                    Arguments.of(new GeocodeResult.NotFound(LOOKED_UP), AddressFlag.NO_MATCH),
                    Arguments.of(new GeocodeResult.GaveUp(LOOKED_UP), AddressFlag.NO_MATCH));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("flaggedResults")
        void GivenADoubtfulResult_WhenRecorded_ThenTheAddressIsFlagged(GeocodeResult result, AddressFlag flag) {
            Customer customer = aCustomer().restored();

            customer.recordGeocode(result, AREA);

            assertThat(customer.flags()).containsExactly(flag);
        }

        @Test
        void GivenAPointExactly300MetresFromARoad_WhenRecorded_ThenItIsNotFlagged() {
            Customer customer = aCustomer().restored();

            customer.recordGeocode(found(MatchQuality.EXACT, Customer.FAR_FROM_ROAD_METRES), AREA);

            assertThat(customer.flags()).isEmpty();
        }

        @Test
        void GivenAPartialMatch_WhenAnExactResultArrives_ThenTheOldFlagIsCleared() {
            Customer customer = aCustomer()
                    .geocodedAt(ALSO_INSIDE, MatchQuality.PARTIAL, AddressFlag.PARTIAL_MATCH)
                    .restored();

            customer.recordGeocode(found(MatchQuality.EXACT, 0), AREA);

            assertThat(customer.location()).isEqualTo(new Location.Geocoded(INSIDE, MatchQuality.EXACT));
            assertThat(customer.flags()).isEmpty();
        }

        @Test
        void GivenNoMatch_WhenRecorded_ThenTheCustomerHasNoLocation() {
            Customer customer = aCustomer().geocodedAt(INSIDE).restored();

            customer.recordGeocode(new GeocodeResult.NotFound(LOOKED_UP), AREA);

            assertThat(customer.location()).isEqualTo(new Location.Missing());
        }

        @Test
        void GivenALookupTheOutboxGaveUpOn_WhenRecorded_ThenTheCustomerWaitsForAHandPlacedLocation() {
            Customer customer =
                    aCustomer().flagged(AddressFlag.CREATED_BY_IMPORT).restored();

            assertThat(customer.recordGeocode(new GeocodeResult.GaveUp(LOOKED_UP), AREA))
                    .isTrue();

            assertThat(customer.location()).isEqualTo(new Location.Missing());
            assertThat(customer.flags()).containsExactly(AddressFlag.NO_MATCH, AddressFlag.CREATED_BY_IMPORT);
        }

        @Test
        void GivenAHandPlacedLocation_WhenAGeocodeArrives_ThenItIsIgnored() {
            Customer customer = aCustomer().placedByHandAt(ALSO_INSIDE).restored();
            CustomerSnapshot before = customer.snapshot();

            assertThat(customer.recordGeocode(found(MatchQuality.EXACT, 0), AREA))
                    .isFalse();
            assertThat(customer.snapshot()).isEqualTo(before);
        }

        @Test
        void GivenAResultForAnAddressTheCustomerNoLongerHas_WhenRecorded_ThenItIsIgnored() {
            Customer customer = aCustomer().restored();
            GeocodeResult stale = new GeocodeResult.Found(
                    NormalizedAddress.of(Address.of("הנביאים 2")), INSIDE, MatchQuality.EXACT, 0);

            assertThat(customer.recordGeocode(stale, AREA)).isFalse();
            assertThat(customer.location()).isEqualTo(new Location.Missing());
        }

        @Test
        void GivenACustomerAnImportCreated_WhenAGeocodeIsRecorded_ThenItsLocationCheckFlagStays() {
            Customer customer =
                    aCustomer().flagged(AddressFlag.CREATED_BY_IMPORT).restored();

            customer.recordGeocode(found(MatchQuality.PARTIAL, 0), AREA);

            assertThat(customer.flags()).containsExactly(AddressFlag.PARTIAL_MATCH, AddressFlag.CREATED_BY_IMPORT);
        }
    }

    @Nested
    class PlacingTheLocation {

        @Test
        void GivenAFlaggedAddress_WhenTheLocationIsPlacedInsideTheArea_ThenItWinsAndClearsEveryFlag() {
            Customer customer = aCustomer()
                    .geocodedAt(INSIDE, AddressFlag.AMBIGUOUS_MATCH, AddressFlag.CREATED_BY_IMPORT)
                    .restored();

            customer.placeLocation(ALSO_INSIDE, AREA);

            assertThat(customer.location()).isEqualTo(new Location.HandPlaced(ALSO_INSIDE));
            assertThat(customer.flags()).isEmpty();
        }

        @Test
        void GivenAPointOnTheAreasBoundary_WhenPlaced_ThenItCountsAsInside() {
            Customer customer = aCustomer().restored();

            customer.placeLocation(GeoPoint.of(32.00, 34.80), AREA);

            assertThat(customer.location()).isInstanceOf(Location.HandPlaced.class);
        }

        @Test
        void GivenAPointOutsideTheArea_WhenPlaced_ThenOutsideServiceArea() {
            Customer customer =
                    aCustomer().geocodedAt(INSIDE, AddressFlag.PARTIAL_MATCH).restored();

            assertRefusedUnchanged(customer, () -> customer.placeLocation(OUTSIDE, AREA), new OutsideServiceArea());
        }
    }

    @Nested
    class Retiring {

        @Test
        void GivenNoPlanUsesIt_WhenRetired_ThenItIsRetiredAtThatTime() {
            Customer customer = aCustomer().restored();

            customer.retire(InUse.NONE, NOW);

            assertThat(customer.state()).isEqualTo(new Customer.State.Retired(NOW));
        }

        @Test
        void GivenAClockSteppedBackBeforeItsCreation_WhenRetired_ThenItIsRetiredAtItsCreationAndStillRestores() {
            Customer customer =
                    aCustomer().createdAt(Instant.parse("2027-06-15T06:00:00Z")).restored();

            customer.retire(InUse.NONE, Instant.parse("2027-06-15T05:59:59Z"));

            assertThat(customer.state()).isEqualTo(new Customer.State.Retired(Instant.parse("2027-06-15T06:00:00Z")));
            assertThat(Customer.restore(customer.snapshot()).snapshot()).isEqualTo(customer.snapshot());
        }

        @Test
        void GivenTheTimeOfItsCreation_WhenRetired_ThenItIsRetiredAtThatTime() {
            Customer customer =
                    aCustomer().createdAt(Instant.parse("2027-06-15T06:00:00Z")).restored();

            customer.retire(InUse.NONE, Instant.parse("2027-06-15T06:00:00Z"));

            assertThat(customer.state()).isEqualTo(new Customer.State.Retired(Instant.parse("2027-06-15T06:00:00Z")));
        }

        @Test
        void GivenPlansNotYetCompletedUseIt_WhenRetired_ThenCustomerInUse() {
            Customer customer = aCustomer().restored();
            PlanId later = PlanId.of(UUID.fromString("0190a5e2-0000-7000-8000-0000000000b2"));
            PlanId earlier = PlanId.of(UUID.fromString("0190a5e2-0000-7000-8000-0000000000b1"));

            assertRefusedUnchanged(
                    customer,
                    () -> customer.retire(new InUse(List.of(later, earlier)), NOW),
                    new CustomerInUse(CUSTOMER, List.of(earlier, later)));
        }
    }

    @Nested
    class Anonymizing {

        @Test
        void GivenACustomerRetiredAYearAgo_WhenAnonymized_ThenItsPersonalDataIsGoneAndItsDefaultsStay() {
            Instant aYearAfter = Instant.parse("2027-06-01T06:00:00Z");
            Customer customer =
                    aCustomer().placedByHandAt(INSIDE).retiredAt(LAST_YEAR).restored();

            customer.anonymize(Optional.empty(), aYearAfter);

            CustomerSnapshot anonymized = customer.snapshot();
            assertThat(anonymized.state()).isEqualTo(new Customer.State.Anonymized(aYearAfter));
            assertThat(anonymized.name()).isEmpty();
            assertThat(anonymized.phone()).isEmpty();
            assertThat(anonymized.address()).isEmpty();
            assertThat(anonymized.note()).isEmpty();
            assertThat(anonymized.location()).isEqualTo(new Location.Missing());
            assertThat(anonymized.defaults()).isEqualTo(DEFAULTS);
            assertThat(customer.normalizedAddress()).isEmpty();
        }

        @Test
        void GivenACustomerRetiredJustUnderAYearAgo_WhenAnonymized_ThenRetentionNotOver() {
            Customer customer = aCustomer().retiredAt(LAST_YEAR).restored();

            assertRefusedUnchanged(
                    customer,
                    () -> customer.anonymize(Optional.empty(), Instant.parse("2027-06-01T05:59:59Z")),
                    new RetentionNotOver());
        }

        @Test
        void GivenAFlaggedCustomer_WhenAnonymizedAndRestored_ThenNoFlagOrLocationIsLeft() {
            Customer customer = aCustomer()
                    .geocodedAt(INSIDE, MatchQuality.PARTIAL, AddressFlag.PARTIAL_MATCH, AddressFlag.FAR_FROM_ROAD)
                    .retiredAt(LAST_YEAR)
                    .restored();

            customer.anonymize(Optional.empty(), NOW);
            Customer reloaded = Customer.restore(customer.snapshot());

            assertThat(reloaded.flags()).isEmpty();
            assertThat(reloaded.location()).isEqualTo(new Location.Missing());
            assertThat(reloaded.snapshot()).isEqualTo(customer.snapshot());
        }

        @Test
        void GivenACustomerNeverRetiredWhoseLastOrderWasAYearAgo_WhenAnonymized_ThenItIsAnonymized() {
            Customer customer = aCustomer().restored();

            customer.anonymize(Optional.of(DeliveryDate.of(LocalDate.of(2026, 6, 15))), NOW);

            assertThat(customer.state()).isEqualTo(new Customer.State.Anonymized(NOW));
        }

        @Test
        void GivenACustomerNeverRetiredWhoseLastOrderWasUnderAYearAgo_WhenAnonymized_ThenRetentionNotOver() {
            Customer customer = aCustomer().restored();

            assertRefusedUnchanged(
                    customer,
                    () -> customer.anonymize(Optional.of(DeliveryDate.of(LocalDate.of(2026, 6, 16))), NOW),
                    new RetentionNotOver());
        }

        @Test
        void GivenACreationLaterThanTheLastOrder_WhenAnonymizedAYearAfterTheOrder_ThenRetentionNotOver() {
            Customer customer =
                    aCustomer().createdAt(Instant.parse("2026-06-20T06:00:00Z")).restored();

            assertRefusedUnchanged(
                    customer,
                    () -> customer.anonymize(Optional.of(DeliveryDate.of(LocalDate.of(2026, 6, 1))), NOW),
                    new RetentionNotOver());
        }

        /** Created at 00:30 in Israel on 15 June 2026, which is still 14 June in UTC. */
        private final Instant justAfterIsraelMidnight = Instant.parse("2026-06-14T21:30:00Z");

        @Test
        void
                GivenACustomerWithNoOrderCreatedJustAfterIsraelMidnight_WhenAnonymizedOnThatDayAYearLater_ThenItIsAnonymized() {
            Customer customer = aCustomer().createdAt(justAfterIsraelMidnight).restored();
            Instant oneMinuteIntoTheDay = Instant.parse("2027-06-14T21:01:00Z");

            customer.anonymize(Optional.empty(), oneMinuteIntoTheDay);

            assertThat(customer.state()).isEqualTo(new Customer.State.Anonymized(oneMinuteIntoTheDay));
        }

        @Test
        void GivenACustomerWithNoOrderCreatedJustAfterIsraelMidnight_WhenAnonymizedTheDayBefore_ThenRetentionNotOver() {
            Customer customer = aCustomer().createdAt(justAfterIsraelMidnight).restored();

            assertRefusedUnchanged(
                    customer,
                    () -> customer.anonymize(Optional.empty(), Instant.parse("2027-06-14T20:59:00Z")),
                    new RetentionNotOver());
        }

        /** 29 February 2028 at 10:00 in Israel. */
        private final Instant leapDay = Instant.parse("2028-02-29T08:00:00Z");

        @Test
        void GivenACustomerCreatedOn29February_WhenAnonymizedOn28FebruaryAYearLater_ThenItIsAnonymized() {
            Customer customer = aCustomer().createdAt(leapDay).restored();
            Instant startOf28February = Instant.parse("2029-02-27T22:00:30Z");

            customer.anonymize(Optional.empty(), startOf28February);

            assertThat(customer.state()).isEqualTo(new Customer.State.Anonymized(startOf28February));
        }

        @Test
        void GivenACustomerCreatedOn29February_WhenAnonymizedOn27FebruaryAYearLater_ThenRetentionNotOver() {
            Customer customer = aCustomer().createdAt(leapDay).restored();

            assertRefusedUnchanged(
                    customer,
                    () -> customer.anonymize(Optional.empty(), Instant.parse("2029-02-27T21:59:00Z")),
                    new RetentionNotOver());
        }
    }

    @Nested
    class EndedCustomers {

        @Test
        void GivenARetiredCustomer_WhenTheAddressChanges_ThenCustomerRetired() {
            Customer customer = aCustomer().retiredAt(LAST_YEAR).restored();

            assertRefusedUnchanged(
                    customer, () -> customer.changeAddress(Address.of("הנביאים 2")), new CustomerRetired(CUSTOMER));
        }

        @Test
        void GivenAnAnonymizedCustomer_WhenTheLocationIsPlaced_ThenCustomerAnonymized() {
            Customer customer = aCustomer().anonymizedAt(LAST_YEAR).restored();

            assertRefusedUnchanged(
                    customer, () -> customer.placeLocation(INSIDE, AREA), new CustomerAnonymized(CUSTOMER));
        }
    }

    /** Each state × each command: the expected result, an ignored call, or the named refusal. */
    @Nested
    class Lifecycle {

        private static final Instant TWO_YEARS_AGO = Instant.parse("2025-06-01T06:00:00Z");
        private static final Address NEW_ADDRESS = Address.of("שד' רוטשילד 1");

        enum CustomerState {
            ACTIVE,
            RETIRED,
            ANONYMIZED
        }

        enum Command {
            CHANGE_ADDRESS(customer -> customer.changeAddress(NEW_ADDRESS), after -> {
                assertThat(after.address()).contains(NEW_ADDRESS);
                assertThat(after.location()).isEqualTo(new Location.Missing());
            }),
            RECORD_GEOCODE(
                    customer -> customer.recordGeocode(found(MatchQuality.EXACT, 0), AREA),
                    after -> assertThat(after.location()).isEqualTo(new Location.Geocoded(INSIDE, MatchQuality.EXACT))),
            PLACE_LOCATION(
                    customer -> customer.placeLocation(INSIDE, AREA),
                    after -> assertThat(after.location()).isEqualTo(new Location.HandPlaced(INSIDE))),
            RETIRE(
                    customer -> customer.retire(InUse.NONE, NOW),
                    after -> assertThat(after.state()).isEqualTo(new Customer.State.Retired(NOW))),
            ANONYMIZE(
                    customer -> customer.anonymize(Optional.of(DeliveryDate.of(LocalDate.of(2025, 1, 1))), NOW),
                    after -> {
                        assertThat(after.state()).isEqualTo(new Customer.State.Anonymized(NOW));
                        assertThat(after.name()).isEmpty();
                    });

            private final Consumer<Customer> call;
            private final Consumer<CustomerSnapshot> expected;

            Command(Consumer<Customer> call, Consumer<CustomerSnapshot> expected) {
                this.call = call;
                this.expected = expected;
            }
        }

        private static Customer inState(CustomerState state) {
            return switch (state) {
                case ACTIVE -> aCustomer().restored();
                case RETIRED -> aCustomer().retiredAt(TWO_YEARS_AGO).restored();
                case ANONYMIZED -> aCustomer().anonymizedAt(TWO_YEARS_AGO).restored();
            };
        }

        /** {@code null} as the error means the command is taken; the ignored geocode has its own test. */
        static Stream<Arguments> matrix() {
            OrdersError retired = new CustomerRetired(CUSTOMER);
            OrdersError anonymized = new CustomerAnonymized(CUSTOMER);
            return Stream.of(
                    Arguments.of(CustomerState.ACTIVE, Command.CHANGE_ADDRESS, null),
                    Arguments.of(CustomerState.ACTIVE, Command.RECORD_GEOCODE, null),
                    Arguments.of(CustomerState.ACTIVE, Command.PLACE_LOCATION, null),
                    Arguments.of(CustomerState.ACTIVE, Command.RETIRE, null),
                    Arguments.of(CustomerState.ACTIVE, Command.ANONYMIZE, null),
                    Arguments.of(CustomerState.RETIRED, Command.CHANGE_ADDRESS, retired),
                    Arguments.of(CustomerState.RETIRED, Command.RECORD_GEOCODE, null),
                    Arguments.of(CustomerState.RETIRED, Command.PLACE_LOCATION, retired),
                    Arguments.of(CustomerState.RETIRED, Command.RETIRE, retired),
                    Arguments.of(CustomerState.RETIRED, Command.ANONYMIZE, null),
                    Arguments.of(CustomerState.ANONYMIZED, Command.CHANGE_ADDRESS, anonymized),
                    Arguments.of(CustomerState.ANONYMIZED, Command.PLACE_LOCATION, anonymized),
                    Arguments.of(CustomerState.ANONYMIZED, Command.RETIRE, anonymized),
                    Arguments.of(CustomerState.ANONYMIZED, Command.ANONYMIZE, anonymized));
        }

        @ParameterizedTest(name = "{0} × {1} → {2}")
        @MethodSource("matrix")
        void GivenACustomerInEachState_WhenEachCommandRuns_ThenItTakesItsResultOrIsRefusedByName(
                CustomerState state, Command command, @Nullable OrdersError expected) {
            Customer customer = inState(state);

            if (expected != null) {
                assertRefusedUnchanged(customer, () -> command.call.accept(customer), expected);
                return;
            }
            command.call.accept(customer);
            command.expected.accept(customer.snapshot());
            assertThat(customer.pendingEvents()).isEmpty();
        }

        @Test
        void GivenAnAnonymizedCustomer_WhenAGeocodeArrives_ThenItIsIgnored() {
            Customer customer = inState(CustomerState.ANONYMIZED);

            assertThat(customer.recordGeocode(found(MatchQuality.EXACT, 0), AREA))
                    .isFalse();
            assertThat(customer.snapshot())
                    .isEqualTo(inState(CustomerState.ANONYMIZED).snapshot());
        }
    }

    @Nested
    class Restoring {

        static Stream<Arguments> builderStates() {
            return Stream.of(
                    Arguments.of("registered", aCustomer().registered()),
                    Arguments.of(
                            "geocoded with flags",
                            aCustomer()
                                    .geocodedAt(INSIDE, AddressFlag.FAR_FROM_ROAD, AddressFlag.NO_MATCH)
                                    .restored()),
                    Arguments.of(
                            "placed by hand", aCustomer().placedByHandAt(INSIDE).restored()),
                    Arguments.of(
                            "created by an import",
                            aCustomer().flagged(AddressFlag.CREATED_BY_IMPORT).restored()),
                    Arguments.of("retired", aCustomer().retiredAt(LAST_YEAR).restored()),
                    Arguments.of(
                            "anonymized", aCustomer().anonymizedAt(LAST_YEAR).restored()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("builderStates")
        void GivenACustomerInEachBuilderState_WhenRestoredFromItsSnapshot_ThenItIsTheSame(
                String state, Customer customer) {
            assertThat(Customer.restore(customer.snapshot()).snapshot()).isEqualTo(customer.snapshot());
        }

        static Stream<Arguments> personalDataLeftAfterAnonymizing() {
            return Stream.of(
                    Arguments.of("a name", (UnaryOperator<Parts>) parts -> new Parts(
                            Optional.of(CustomerBuilder.NAME),
                            parts.phone(),
                            parts.address(),
                            parts.note(),
                            parts.location(),
                            parts.flags())),
                    Arguments.of("a phone", (UnaryOperator<Parts>) parts -> new Parts(
                            parts.name(),
                            Optional.of(CustomerBuilder.PHONE),
                            parts.address(),
                            parts.note(),
                            parts.location(),
                            parts.flags())),
                    Arguments.of("an address", (UnaryOperator<Parts>) parts -> new Parts(
                            parts.name(),
                            parts.phone(),
                            Optional.of(ADDRESS),
                            parts.note(),
                            parts.location(),
                            parts.flags())),
                    Arguments.of("a note", (UnaryOperator<Parts>) parts -> new Parts(
                            parts.name(),
                            parts.phone(),
                            parts.address(),
                            Optional.of(CustomerBuilder.NOTES),
                            parts.location(),
                            parts.flags())),
                    Arguments.of("a location", (UnaryOperator<Parts>) parts -> new Parts(
                            parts.name(),
                            parts.phone(),
                            parts.address(),
                            parts.note(),
                            new Location.HandPlaced(INSIDE),
                            parts.flags())),
                    Arguments.of("a flag", (UnaryOperator<Parts>) parts -> new Parts(
                            parts.name(),
                            parts.phone(),
                            parts.address(),
                            parts.note(),
                            parts.location(),
                            EnumSet.of(AddressFlag.NO_MATCH))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("personalDataLeftAfterAnonymizing")
        void GivenAStoredAnonymizedCustomerWithPersonalData_WhenRestored_ThenStoredStateInvalid(
                String field, UnaryOperator<Parts> damage) {
            CustomerSnapshot damaged = copy(aCustomer().anonymizedAt(LAST_YEAR).snapshot(), damage);

            assertThatThrownBy(() -> Customer.restore(damaged))
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail())
                                    .isEqualTo("an anonymized customer holds personal data"));
        }

        static Stream<Arguments> requiredFieldsMissing() {
            return Stream.of(
                    Arguments.of("no name", (UnaryOperator<Parts>) parts -> new Parts(
                            Optional.empty(),
                            parts.phone(),
                            parts.address(),
                            parts.note(),
                            parts.location(),
                            parts.flags())),
                    Arguments.of("no phone", (UnaryOperator<Parts>) parts -> new Parts(
                            parts.name(),
                            Optional.empty(),
                            parts.address(),
                            parts.note(),
                            parts.location(),
                            parts.flags())),
                    Arguments.of("no address", (UnaryOperator<Parts>) parts -> new Parts(
                            parts.name(),
                            parts.phone(),
                            Optional.empty(),
                            parts.note(),
                            parts.location(),
                            parts.flags())));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("requiredFieldsMissing")
        void GivenAStoredActiveCustomerMissingARequiredField_WhenRestored_ThenStoredStateInvalid(
                String field, UnaryOperator<Parts> damage) {
            CustomerSnapshot damaged = copy(aCustomer().snapshot(), damage);

            assertThatThrownBy(() -> Customer.restore(damaged))
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail())
                                    .isEqualTo("a customer lacks its name, phone or address"));
        }

        @Test
        void GivenAStoredHandPlacedLocationWithAFlag_WhenRestored_ThenStoredStateInvalid() {
            CustomerSnapshot damaged = copy(
                    aCustomer().placedByHandAt(INSIDE).snapshot(),
                    parts -> new Parts(
                            parts.name(),
                            parts.phone(),
                            parts.address(),
                            parts.note(),
                            parts.location(),
                            EnumSet.of(AddressFlag.PARTIAL_MATCH)));

            assertThatThrownBy(() -> Customer.restore(damaged))
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail())
                                    .isEqualTo("a hand-placed location carries an address flag"));
        }

        @Test
        void GivenAStoredRetirementBeforeTheCreation_WhenRestored_ThenStoredStateInvalid() {
            CustomerBuilder damaged = aCustomer().createdAt(NOW).retiredAt(LAST_YEAR);

            assertThatThrownBy(damaged::restored)
                    .isInstanceOfSatisfying(
                            StoredStateInvalid.class,
                            invalid -> assertThat(invalid.detail())
                                    .isEqualTo("the customer's state is older than its creation"));
        }
    }
}
