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

import com.dispatchflow.orders.domain.OrdersError.CustomerAnonymized;
import com.dispatchflow.orders.domain.OrdersError.CustomerInUse;
import com.dispatchflow.orders.domain.OrdersError.CustomerRetired;
import com.dispatchflow.orders.domain.OrdersError.OutsideServiceArea;
import com.dispatchflow.orders.domain.OrdersError.RetentionNotOver;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PlanId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
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
        CustomerDescription before = CustomerDescription.of(customer);
        assertRefused(call, expected);
        assertThat(CustomerDescription.of(customer)).isEqualTo(before);
        assertThat(customer.pendingEvents()).isEmpty();
    }

    private static GeocodeResult found(MatchQuality quality, double metresToRoad) {
        return new GeocodeResult.Found(LOOKED_UP, INSIDE, quality, metresToRoad);
    }

    @Nested
    class Registering {

        @Test
        void GivenItsDetailsAndTheCompanysDefaults_WhenRegistered_ThenItIsActiveWithNoLocationAndNoFlag() {
            Customer customer = aCustomer().registered();

            assertThat(customer.state()).isEqualTo(new Customer.State.Active());
            assertThat(customer.location()).isEqualTo(new Location.Missing());
            assertThat(customer.flags()).isEmpty();
            assertThat(customer.note()).contains(CustomerBuilder.NOTES);
            assertThat(customer.createdAt()).isEqualTo(CREATED);
            assertThat(customer.savedVersion()).isEmpty();
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
            Customer customer = aCustomer().placedByHandAt(INSIDE).build();

            boolean geocodeAgain = customer.changeAddress(Address.of("שד' רוטשילד 1"));

            assertThat(geocodeAgain).isTrue();
            assertThat(customer.location()).isEqualTo(new Location.Missing());
            assertThat(customer.normalizedAddress()).contains(NormalizedAddress.of(Address.of("שדרות רוטשילד 1")));
        }

        @Test
        void GivenFlags_WhenTheAddressChanges_ThenTheyAreCleared() {
            Customer customer = aCustomer()
                    .geocodedAt(INSIDE, MatchQuality.PARTIAL, AddressFlag.PARTIAL_MATCH, AddressFlag.FAR_FROM_ROAD)
                    .build();

            customer.changeAddress(Address.of("שד' רוטשילד 1"));

            assertThat(customer.flags()).isEmpty();
        }

        @Test
        void GivenTheSameAddress_WhenChanged_ThenNothingChanges() {
            Customer customer = aCustomer().placedByHandAt(INSIDE).build();
            CustomerDescription before = CustomerDescription.of(customer);

            assertThat(customer.changeAddress(ADDRESS)).isFalse();
            assertThat(CustomerDescription.of(customer)).isEqualTo(before);
        }
    }

    @Nested
    class RecordingAGeocode {

        @Test
        void GivenAnExactMatchInsideTheAreaNearARoad_WhenRecorded_ThenItIsTheLocationWithNoFlag() {
            Customer customer = aCustomer().build();

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
            Customer customer = aCustomer().build();

            customer.recordGeocode(result, AREA);

            assertThat(customer.flags()).containsExactly(flag);
        }

        @Test
        void GivenAPointExactly300MetresFromARoad_WhenRecorded_ThenItIsNotFlagged() {
            Customer customer = aCustomer().build();

            customer.recordGeocode(found(MatchQuality.EXACT, Customer.FAR_FROM_ROAD_METRES), AREA);

            assertThat(customer.flags()).isEmpty();
        }

        @Test
        void GivenAPartialMatch_WhenAnExactResultArrives_ThenTheOldFlagIsCleared() {
            Customer customer = aCustomer()
                    .geocodedAt(ALSO_INSIDE, MatchQuality.PARTIAL, AddressFlag.PARTIAL_MATCH)
                    .build();

            customer.recordGeocode(found(MatchQuality.EXACT, 0), AREA);

            assertThat(customer.location()).isEqualTo(new Location.Geocoded(INSIDE, MatchQuality.EXACT));
            assertThat(customer.flags()).isEmpty();
        }

        @Test
        void GivenNoMatch_WhenRecorded_ThenTheCustomerHasNoLocation() {
            Customer customer = aCustomer().geocodedAt(INSIDE).build();

            customer.recordGeocode(new GeocodeResult.NotFound(LOOKED_UP), AREA);

            assertThat(customer.location()).isEqualTo(new Location.Missing());
        }

        @Test
        void GivenALookupTheOutboxGaveUpOn_WhenRecorded_ThenTheCustomerWaitsForAHandPlacedLocation() {
            Customer customer = aCustomer().createdByImport().build();

            assertThat(customer.recordGeocode(new GeocodeResult.GaveUp(LOOKED_UP), AREA))
                    .isTrue();

            assertThat(customer.location()).isEqualTo(new Location.Missing());
            assertThat(customer.flags()).containsExactly(AddressFlag.NO_MATCH, AddressFlag.CREATED_BY_IMPORT);
        }

        @Test
        void GivenAHandPlacedLocation_WhenAGeocodeArrives_ThenItIsIgnored() {
            Customer customer = aCustomer().placedByHandAt(ALSO_INSIDE).build();
            CustomerDescription before = CustomerDescription.of(customer);

            assertThat(customer.recordGeocode(found(MatchQuality.EXACT, 0), AREA))
                    .isFalse();
            assertThat(CustomerDescription.of(customer)).isEqualTo(before);
        }

        @Test
        void GivenAResultForAnAddressTheCustomerNoLongerHas_WhenRecorded_ThenItIsIgnored() {
            Customer customer = aCustomer().build();
            GeocodeResult stale = new GeocodeResult.Found(
                    NormalizedAddress.of(Address.of("הנביאים 2")), INSIDE, MatchQuality.EXACT, 0);

            assertThat(customer.recordGeocode(stale, AREA)).isFalse();
            assertThat(customer.location()).isEqualTo(new Location.Missing());
        }

        @Test
        void GivenACustomerAnImportCreated_WhenAGeocodeIsRecorded_ThenItsLocationCheckFlagStays() {
            Customer customer = aCustomer().createdByImport().build();

            customer.recordGeocode(found(MatchQuality.PARTIAL, 0), AREA);

            assertThat(customer.flags()).containsExactly(AddressFlag.PARTIAL_MATCH, AddressFlag.CREATED_BY_IMPORT);
        }
    }

    @Nested
    class PlacingTheLocation {

        @Test
        void GivenAFlaggedAddress_WhenTheLocationIsPlacedInsideTheArea_ThenItWinsAndClearsEveryFlag() {
            Customer customer = aCustomer()
                    .geocodedAt(
                            INSIDE, MatchQuality.AMBIGUOUS, AddressFlag.AMBIGUOUS_MATCH, AddressFlag.CREATED_BY_IMPORT)
                    .build();

            customer.placeLocation(ALSO_INSIDE, AREA);

            assertThat(customer.location()).isEqualTo(new Location.HandPlaced(ALSO_INSIDE));
            assertThat(customer.flags()).isEmpty();
        }

        @Test
        void GivenAPointOnTheAreasBoundary_WhenPlaced_ThenItCountsAsInside() {
            Customer customer = aCustomer().build();

            customer.placeLocation(GeoPoint.of(32.00, 34.80), AREA);

            assertThat(customer.location()).isInstanceOf(Location.HandPlaced.class);
        }

        @Test
        void GivenAPointOutsideTheArea_WhenPlaced_ThenOutsideServiceArea() {
            Customer customer = aCustomer()
                    .geocodedAt(INSIDE, MatchQuality.PARTIAL, AddressFlag.PARTIAL_MATCH)
                    .build();

            assertRefusedUnchanged(customer, () -> customer.placeLocation(OUTSIDE, AREA), new OutsideServiceArea());
        }
    }

    @Nested
    class Retiring {

        @Test
        void GivenNoPlanUsesIt_WhenRetired_ThenItIsRetiredAtThatTime() {
            Customer customer = aCustomer().build();

            customer.retire(InUse.NONE, NOW);

            assertThat(customer.state()).isEqualTo(new Customer.State.Retired(NOW));
        }

        @Test
        void GivenAClockSteppedBackBeforeItsCreation_WhenRetired_ThenItIsRetiredAtItsCreationNotBeforeIt() {
            Customer customer =
                    aCustomer().createdAt(Instant.parse("2027-06-15T06:00:00Z")).build();

            customer.retire(InUse.NONE, Instant.parse("2027-06-15T05:59:59Z"));

            // The retirement's end check runs the load check's rules, so it would have failed on a
            // retirement older than the creation; CustomerPersistenceIT loads such a row and refuses it.
            assertThat(customer.state()).isEqualTo(new Customer.State.Retired(Instant.parse("2027-06-15T06:00:00Z")));
        }

        @Test
        void GivenTheTimeOfItsCreation_WhenRetired_ThenItIsRetiredAtThatTime() {
            Customer customer =
                    aCustomer().createdAt(Instant.parse("2027-06-15T06:00:00Z")).build();

            customer.retire(InUse.NONE, Instant.parse("2027-06-15T06:00:00Z"));

            assertThat(customer.state()).isEqualTo(new Customer.State.Retired(Instant.parse("2027-06-15T06:00:00Z")));
        }

        @Test
        void GivenPlansNotYetCompletedUseIt_WhenRetired_ThenCustomerInUse() {
            Customer customer = aCustomer().build();
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
                    aCustomer().placedByHandAt(INSIDE).retiredAt(LAST_YEAR).build();

            customer.anonymize(Optional.empty(), aYearAfter);

            assertThat(customer.state()).isEqualTo(new Customer.State.Anonymized(aYearAfter));
            assertThat(customer.name()).isEmpty();
            assertThat(customer.phone()).isEmpty();
            assertThat(customer.address()).isEmpty();
            assertThat(customer.note()).isEmpty();
            assertThat(customer.location()).isEqualTo(new Location.Missing());
            assertThat(customer.orderDefaults()).isEqualTo(DEFAULTS);
            assertThat(customer.normalizedAddress()).isEmpty();
        }

        @Test
        void GivenACustomerRetiredJustUnderAYearAgo_WhenAnonymized_ThenRetentionNotOver() {
            Customer customer = aCustomer().retiredAt(LAST_YEAR).build();

            assertRefusedUnchanged(
                    customer,
                    () -> customer.anonymize(Optional.empty(), Instant.parse("2027-06-01T05:59:59Z")),
                    new RetentionNotOver());
        }

        @Test
        void GivenAFlaggedCustomer_WhenAnonymized_ThenNoFlagOrLocationIsLeft() {
            Customer customer = aCustomer()
                    .geocodedAt(INSIDE, MatchQuality.PARTIAL, AddressFlag.PARTIAL_MATCH, AddressFlag.FAR_FROM_ROAD)
                    .retiredAt(LAST_YEAR)
                    .build();

            customer.anonymize(Optional.empty(), NOW);

            assertThat(customer.flags()).isEmpty();
            assertThat(customer.location()).isEqualTo(new Location.Missing());
        }

        @Test
        void GivenACustomerNeverRetiredWhoseLastOrderWasAYearAgo_WhenAnonymized_ThenItIsAnonymized() {
            Customer customer = aCustomer().build();

            customer.anonymize(Optional.of(DeliveryDate.of(LocalDate.of(2026, 6, 15))), NOW);

            assertThat(customer.state()).isEqualTo(new Customer.State.Anonymized(NOW));
        }

        @Test
        void GivenACustomerNeverRetiredWhoseLastOrderWasUnderAYearAgo_WhenAnonymized_ThenRetentionNotOver() {
            Customer customer = aCustomer().build();

            assertRefusedUnchanged(
                    customer,
                    () -> customer.anonymize(Optional.of(DeliveryDate.of(LocalDate.of(2026, 6, 16))), NOW),
                    new RetentionNotOver());
        }

        @Test
        void GivenACreationLaterThanTheLastOrder_WhenAnonymizedAYearAfterTheOrder_ThenRetentionNotOver() {
            Customer customer =
                    aCustomer().createdAt(Instant.parse("2026-06-20T06:00:00Z")).build();

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
            Customer customer = aCustomer().createdAt(justAfterIsraelMidnight).build();
            Instant oneMinuteIntoTheDay = Instant.parse("2027-06-14T21:01:00Z");

            customer.anonymize(Optional.empty(), oneMinuteIntoTheDay);

            assertThat(customer.state()).isEqualTo(new Customer.State.Anonymized(oneMinuteIntoTheDay));
        }

        @Test
        void GivenACustomerWithNoOrderCreatedJustAfterIsraelMidnight_WhenAnonymizedTheDayBefore_ThenRetentionNotOver() {
            Customer customer = aCustomer().createdAt(justAfterIsraelMidnight).build();

            assertRefusedUnchanged(
                    customer,
                    () -> customer.anonymize(Optional.empty(), Instant.parse("2027-06-14T20:59:00Z")),
                    new RetentionNotOver());
        }

        /** 29 February 2028 at 10:00 in Israel. */
        private final Instant leapDay = Instant.parse("2028-02-29T08:00:00Z");

        @Test
        void GivenACustomerCreatedOn29February_WhenAnonymizedOn28FebruaryAYearLater_ThenItIsAnonymized() {
            Customer customer = aCustomer().createdAt(leapDay).build();
            Instant startOf28February = Instant.parse("2029-02-27T22:00:30Z");

            customer.anonymize(Optional.empty(), startOf28February);

            assertThat(customer.state()).isEqualTo(new Customer.State.Anonymized(startOf28February));
        }

        @Test
        void GivenACustomerCreatedOn29February_WhenAnonymizedOn27FebruaryAYearLater_ThenRetentionNotOver() {
            Customer customer = aCustomer().createdAt(leapDay).build();

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
            Customer customer = aCustomer().retiredAt(LAST_YEAR).build();

            assertRefusedUnchanged(
                    customer, () -> customer.changeAddress(Address.of("הנביאים 2")), new CustomerRetired(CUSTOMER));
        }

        @Test
        void GivenAnAnonymizedCustomer_WhenTheLocationIsPlaced_ThenCustomerAnonymized() {
            Customer customer = aCustomer().anonymizedAt(LAST_YEAR).build();

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
            private final Consumer<Customer> expected;

            Command(Consumer<Customer> call, Consumer<Customer> expected) {
                this.call = call;
                this.expected = expected;
            }
        }

        private static Customer inState(CustomerState state) {
            return switch (state) {
                case ACTIVE -> aCustomer().build();
                case RETIRED -> aCustomer().retiredAt(TWO_YEARS_AGO).build();
                case ANONYMIZED -> aCustomer().anonymizedAt(LAST_YEAR).build();
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
            command.expected.accept(customer);
            assertThat(customer.pendingEvents()).isEmpty();
        }

        @Test
        void GivenAnAnonymizedCustomer_WhenAGeocodeArrives_ThenItIsIgnored() {
            Customer customer = inState(CustomerState.ANONYMIZED);

            assertThat(customer.recordGeocode(found(MatchQuality.EXACT, 0), AREA))
                    .isFalse();
            assertThat(CustomerDescription.of(customer))
                    .isEqualTo(CustomerDescription.of(inState(CustomerState.ANONYMIZED)));
        }
    }
}
