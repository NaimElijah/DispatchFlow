package com.dispatchflow.fleet.domain.driver;

import static com.dispatchflow.fleet.domain.driver.DriverBuilder.ANONYMIZED_AT;
import static com.dispatchflow.fleet.domain.driver.DriverBuilder.DANA;
import static com.dispatchflow.fleet.domain.driver.DriverBuilder.DANA_CONTACT;
import static com.dispatchflow.fleet.domain.driver.DriverBuilder.DEFAULT_SHIFT;
import static com.dispatchflow.fleet.domain.driver.DriverBuilder.DEFAULT_VEHICLE;
import static com.dispatchflow.fleet.domain.driver.DriverBuilder.LOADED;
import static com.dispatchflow.fleet.domain.driver.DriverBuilder.RETIRED_AT;
import static com.dispatchflow.fleet.domain.driver.DriverBuilder.aDriver;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.fleet.domain.FleetError.DriverInUse;
import com.dispatchflow.fleet.domain.FleetError.NotAnActiveDriver;
import com.dispatchflow.fleet.domain.FleetError.RetentionNotOver;
import com.dispatchflow.fleet.domain.PlanUse;
import com.dispatchflow.fleet.domain.PlanUses;
import com.dispatchflow.fleet.domain.RosterDefault;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.fleet.domain.register.FleetRegisterBuilder;
import com.dispatchflow.fleet.events.DriverRetired;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A driver's invariants and lifecycle (DL §4.2, §9.7): registration, defaults (FR-06, FR-189),
 * weekly shifts (FR-79), retirement (BR-43, FR-305, FR-307) and anonymization (BR-98, NFR-184).
 */
class DriverTest {

    private static final Instant NOW = Instant.parse("2027-06-15T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 15);
    private static final PlanId PLAN = PlanId.of(UUID.fromString("0190a5e2-0000-7000-a000-000000000001"));
    private static final ShiftHours MORNING = ShiftHours.of(LocalTime.of(6, 0), LocalTime.of(14, 0));
    private static final DeliveryDate SUNDAY = DeliveryDate.of(LocalDate.of(2027, 6, 13));
    private static final DeliveryDate MONDAY = DeliveryDate.of(LocalDate.of(2027, 6, 14));

    private static void assertRefusedUnchanged(Driver driver, ThrowingCallable call, DomainError expected) {
        DriverSnapshot before = driver.snapshot();

        assertRefused(call, expected);

        assertThat(driver.snapshot()).isEqualTo(before);
        assertThat(driver.pendingEvents()).isEmpty();
    }

    // --- registration and changes

    @Test
    void GivenAnActiveAccountWithTheDriverRole_WhenRegistered_ThenTheDriverIsActiveWithTheirDefaults() {
        Driver driver = Driver.register(DANA, DANA_CONTACT, DEFAULT_SHIFT, DEFAULT_VEHICLE, Standing.ACTIVE);

        assertThat(driver.id()).isEqualTo(DANA);
        assertThat(driver.snapshot().version()).isNull();
        assertThat(driver.snapshot())
                .isEqualTo(new DriverSnapshot(
                        DANA,
                        null,
                        new DriverStatus.Active(),
                        Optional.of(DANA_CONTACT),
                        DEFAULT_SHIFT,
                        DEFAULT_VEHICLE,
                        WeeklyShifts.none()));
        assertThat(driver.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAnAccountThatIsNotAnActiveDriver_WhenRegistered_ThenNotAnActiveDriver() {
        assertRefused(
                () -> Driver.register(DANA, DANA_CONTACT, DEFAULT_SHIFT, DEFAULT_VEHICLE, Standing.NOT_ACTIVE),
                new NotAnActiveDriver(DANA));
    }

    @Test
    void GivenAnActiveDriver_WhenTheirDetailsChange_ThenTheNewContactAndDefaultsApply() {
        Driver driver = aDriver().build();
        DriverContact newContact = new DriverContact(DriverName.of("Dana Cohen"), PhoneNumber.parse("052-345-6780"));

        driver.changeDetails(newContact, MORNING, FleetRegisterBuilder.vehicleId(2));

        assertThat(driver.snapshot())
                .isEqualTo(aDriver()
                        .withContact(Optional.of(newContact))
                        .withDefaults(MORNING, FleetRegisterBuilder.vehicleId(2))
                        .snapshot());
        assertThat(driver.pendingEvents()).isEmpty();
    }

    @Test
    void GivenARetiredDriver_WhenTheirDetailsChange_ThenNotAnActiveDriver() {
        Driver driver = aDriver().retired().build();

        assertRefusedUnchanged(
                driver,
                () -> driver.changeDetails(DANA_CONTACT, MORNING, DEFAULT_VEHICLE),
                new NotAnActiveDriver(DANA));
    }

    @Test
    void GivenAnActiveDriver_WhenTheirWeeklyShiftsChange_ThenTheNewTemplateApplies() {
        Driver driver = aDriver().build();
        WeeklyShifts week = WeeklyShifts.of(Map.of(DayOfWeek.SUNDAY, MORNING));

        driver.changeWeeklyShifts(week);

        assertThat(driver.snapshot().weeklyShifts()).isEqualTo(week);
    }

    // --- the date's default (FR-06, FR-79, FR-189, ADR-103)

    @Test
    void GivenNoWeeklyShifts_WhenAnyDatesDefaultIsAsked_ThenItIsTheDefaultShiftAndVehicle() {
        Driver driver = aDriver().build();

        assertThat(driver.rosterDefaultOn(SUNDAY, Standing.ACTIVE))
                .contains(RosterDefault.working(DANA, DEFAULT_SHIFT, DEFAULT_VEHICLE, Standing.ACTIVE));
        assertThat(driver.rosterDefaultOn(MONDAY, Standing.ACTIVE))
                .contains(RosterDefault.working(DANA, DEFAULT_SHIFT, DEFAULT_VEHICLE, Standing.ACTIVE));
    }

    @Test
    void GivenWeeklyShifts_WhenADayWithAShiftIsAsked_ThenItIsThatShiftWithTheDefaultVehicle() {
        Driver driver = aDriver()
                .withWeeklyShifts(WeeklyShifts.of(Map.of(DayOfWeek.SUNDAY, MORNING)))
                .build();

        assertThat(driver.rosterDefaultOn(SUNDAY, Standing.ACTIVE))
                .contains(RosterDefault.working(DANA, MORNING, DEFAULT_VEHICLE, Standing.ACTIVE));
    }

    @Test
    void GivenWeeklyShifts_WhenADayWithoutAShiftIsAsked_ThenTheDefaultHasNoHoursButTheDefaultVehicle() {
        Driver driver = aDriver()
                .withWeeklyShifts(WeeklyShifts.of(Map.of(DayOfWeek.SUNDAY, MORNING)))
                .build();

        assertThat(driver.rosterDefaultOn(MONDAY, Standing.ACTIVE))
                .contains(RosterDefault.dayOff(DANA, DEFAULT_VEHICLE, Standing.ACTIVE));
    }

    @Test
    void GivenAnAccountThatIsNoLongerAnActiveDriver_WhenADatesDefaultIsAsked_ThenTheDefaultCarriesThatStanding() {
        Driver driver = aDriver().build();

        assertThat(driver.rosterDefaultOn(SUNDAY, Standing.NOT_ACTIVE))
                .contains(RosterDefault.working(DANA, DEFAULT_SHIFT, DEFAULT_VEHICLE, Standing.NOT_ACTIVE));
    }

    @Test
    void GivenARetiredDriver_WhenADatesDefaultIsAsked_ThenThereIsNone() {
        assertThat(aDriver().retired().build().rosterDefaultOn(SUNDAY, Standing.ACTIVE))
                .isEmpty();
        assertThat(aDriver().anonymized().build().rosterDefaultOn(SUNDAY, Standing.ACTIVE))
                .isEmpty();
    }

    // --- retirement (FR-305, FR-307, ADR-103)

    @Test
    void GivenAnActiveDriverNoPlanUses_WhenRetired_ThenTheyAreRetiredAndDriverRetiredIsRaised() {
        Driver driver = aDriver().build();

        driver.retire(PlanUses.none(), TODAY, NOW);

        assertThat(driver.snapshot().status()).isEqualTo(new DriverStatus.Retired(NOW));
        assertThat(driver.snapshot().contact()).contains(DANA_CONTACT);
        assertThat(driver.pendingEvents())
                .containsExactly(
                        new DriverRetired(new EventId("DriverRetired", DANA.value(), LOADED.next(), 0), NOW, DANA));
    }

    @Test
    void GivenAnUncancelledPlanForALaterDateUsesTheDriver_WhenRetired_ThenDriverInUse() {
        Driver driver = aDriver().build();
        PlanUses uses = PlanUses.of(List.of(PlanUse.uncancelled(PLAN, DeliveryDate.of(TODAY.plusDays(3)))));

        assertRefusedUnchanged(
                driver,
                () -> driver.retire(uses, TODAY, NOW),
                new DriverInUse(DANA, PLAN, DeliveryDate.of(TODAY.plusDays(3))));
    }

    @Test
    void GivenOnlyAPastPlanAndACancelledOneUseTheDriver_WhenRetired_ThenTheyAreRetired() {
        Driver driver = aDriver().build();
        PlanUses uses = PlanUses.of(List.of(
                PlanUse.uncancelled(PLAN, DeliveryDate.of(TODAY.minusDays(1))),
                PlanUse.cancelled(PLAN, DeliveryDate.of(TODAY))));

        driver.retire(uses, TODAY, NOW);

        assertThat(driver.snapshot().status()).isEqualTo(new DriverStatus.Retired(NOW));
    }

    @Test
    void GivenARetiredDriver_WhenRetiredAgain_ThenNotAnActiveDriver() {
        Driver driver = aDriver().retired().build();

        assertRefusedUnchanged(driver, () -> driver.retire(PlanUses.none(), TODAY, NOW), new NotAnActiveDriver(DANA));
    }

    // --- anonymization (BR-98, NFR-184, ADR-075)

    @Test
    void GivenADriverRetiredAYearAgo_WhenAnonymized_ThenTheirNameAndPhoneAreGoneAndTheRecordStays() {
        Driver driver = aDriver().retired().build();

        driver.anonymize(ANONYMIZED_AT);

        assertThat(driver.snapshot()).isEqualTo(aDriver().anonymized().snapshot());
        assertThat(driver.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADriverRetiredAlmostAYearAgo_WhenAnonymized_ThenRetentionNotOver() {
        Driver driver = aDriver().retired().build();

        assertRefusedUnchanged(driver, () -> driver.anonymize(ANONYMIZED_AT.minusSeconds(1)), new RetentionNotOver());
    }

    @Test
    void GivenAnActiveDriver_WhenAnonymized_ThenRetentionNotOver() {
        Driver driver = aDriver().build();

        assertRefusedUnchanged(driver, () -> driver.anonymize(NOW), new RetentionNotOver());
    }

    @Test
    void GivenADriverRetiredBeforeTheClockMoved_WhenAYearLaterTheClockHasMoved_ThenTheYearEndsAtTheSameIsraelTime() {
        // Retired at 12:00 on 25 March 2027, still winter time (UTC+2). The clock moves on 24 March
        // in 2028, so 12:00 on 25 March 2028 is summer time (UTC+3), an hour earlier in UTC.
        Instant retired = Instant.parse("2027-03-25T10:00:00Z");
        Instant yearLater = Instant.parse("2028-03-25T09:00:00Z");
        Driver driver = aDriver().withStatus(new DriverStatus.Retired(retired)).build();

        assertRefusedUnchanged(driver, () -> driver.anonymize(yearLater.minusSeconds(1)), new RetentionNotOver());
        driver.anonymize(yearLater);
        assertThat(driver.snapshot().status()).isEqualTo(new DriverStatus.Anonymized(Optional.of(retired), yearLater));
    }

    @Test
    void GivenAnAnonymizedDriver_WhenAnonymizedAgain_ThenNothingChanges() {
        Driver driver = aDriver().anonymized().build();

        driver.anonymize(ANONYMIZED_AT.plusSeconds(86_400));

        assertThat(driver.snapshot()).isEqualTo(aDriver().anonymized().snapshot());
        assertThat(driver.pendingEvents()).isEmpty();
    }

    // --- anonymized with the account (NFR-258, ADR-103)

    @Test
    void GivenAnActiveDriver_WhenTheirAccountIsAnonymized_ThenTheirNameAndPhoneAreGoneAndTheyAreNoLongerActive() {
        Driver driver = aDriver().build();

        driver.anonymizeWithAccount(NOW);

        assertThat(driver.snapshot())
                .isEqualTo(aDriver()
                        .withStatus(new DriverStatus.Anonymized(Optional.empty(), NOW))
                        .withContact(Optional.empty())
                        .snapshot());
        assertThat(driver.rosterDefaultOn(SUNDAY, Standing.ACTIVE)).isEmpty();
        assertThat(driver.pendingEvents()).isEmpty();
    }

    @Test
    void
            GivenADriverRetiredLessThanAYearAgo_WhenTheirAccountIsAnonymized_ThenTheyAreAnonymizedKeepingTheirRetirementTime() {
        Driver driver = aDriver().retired().build();
        Instant soon = RETIRED_AT.plusSeconds(86_400);

        driver.anonymizeWithAccount(soon);

        assertThat(driver.snapshot().status()).isEqualTo(new DriverStatus.Anonymized(Optional.of(RETIRED_AT), soon));
        assertThat(driver.snapshot().contact()).isEmpty();
    }

    @Test
    void GivenADriverRetiredAfterTheirAccountWasAnonymized_WhenTheLateEventIsHeard_ThenTheSavedStateRestores() {
        Driver driver = aDriver().retired().build();
        driver.anonymizeWithAccount(RETIRED_AT.minusSeconds(3_600));
        assertThatCode(() -> Driver.restore(driver.snapshot())).doesNotThrowAnyException();
    }

    @Test
    void
            GivenADriverRetiredAfterTheirAccountWasAnonymized_WhenTheLateEventIsHeard_ThenTheyAreAnonymizedAtTheirRetirement() {
        Driver driver = aDriver().retired().build();

        driver.anonymizeWithAccount(RETIRED_AT.minusSeconds(3_600));

        assertThat(driver.snapshot().status())
                .isEqualTo(new DriverStatus.Anonymized(Optional.of(RETIRED_AT), RETIRED_AT));
        assertThat(driver.snapshot().contact()).isEmpty();
    }

    @Test
    void GivenAnAnonymizedDriver_WhenTheirAccountIsAnonymizedAgain_ThenNothingChanges() {
        Driver driver = aDriver().anonymized().build();

        driver.anonymizeWithAccount(NOW.plusSeconds(86_400));

        assertThat(driver.snapshot()).isEqualTo(aDriver().anonymized().snapshot());
        assertThat(driver.pendingEvents()).isEmpty();
    }

    // --- the lifecycle

    enum State {
        ACTIVE,
        RETIRED,
        ANONYMIZED
    }

    interface Command {
        void run(Driver driver);
    }

    private static Driver inState(State state) {
        return switch (state) {
            case ACTIVE -> aDriver().build();
            case RETIRED -> aDriver().retired().build();
            case ANONYMIZED -> aDriver().anonymized().build();
        };
    }

    static Stream<Arguments> lifecycle() {
        Command change = driver -> driver.changeDetails(DANA_CONTACT, MORNING, DEFAULT_VEHICLE);
        Command weekly = driver -> driver.changeWeeklyShifts(WeeklyShifts.of(Map.of(DayOfWeek.MONDAY, MORNING)));
        Command retire = driver -> driver.retire(PlanUses.none(), TODAY, NOW);
        Command anonymize = driver -> driver.anonymize(ANONYMIZED_AT);
        Command withAccount = driver -> driver.anonymizeWithAccount(NOW);
        DomainError notActive = new NotAnActiveDriver(DANA);
        DriverStatus retiredNow = new DriverStatus.Retired(NOW);
        DriverStatus anonymized = new DriverStatus.Anonymized(Optional.of(RETIRED_AT), ANONYMIZED_AT);
        return Stream.of(
                Arguments.of(State.ACTIVE, "change details", change, new DriverStatus.Active(), null),
                Arguments.of(State.ACTIVE, "change weekly shifts", weekly, new DriverStatus.Active(), null),
                Arguments.of(State.ACTIVE, "retire", retire, retiredNow, null),
                Arguments.of(State.ACTIVE, "anonymize", anonymize, null, new RetentionNotOver()),
                Arguments.of(
                        State.ACTIVE,
                        "anonymize with the account",
                        withAccount,
                        new DriverStatus.Anonymized(Optional.empty(), NOW),
                        null),
                Arguments.of(State.RETIRED, "change details", change, null, notActive),
                Arguments.of(State.RETIRED, "change weekly shifts", weekly, null, notActive),
                Arguments.of(State.RETIRED, "retire", retire, null, notActive),
                Arguments.of(State.RETIRED, "anonymize", anonymize, anonymized, null),
                Arguments.of(
                        State.RETIRED,
                        "anonymize with the account",
                        withAccount,
                        new DriverStatus.Anonymized(Optional.of(RETIRED_AT), NOW),
                        null),
                Arguments.of(State.ANONYMIZED, "change details", change, null, notActive),
                Arguments.of(State.ANONYMIZED, "change weekly shifts", weekly, null, notActive),
                Arguments.of(State.ANONYMIZED, "retire", retire, null, notActive),
                Arguments.of(State.ANONYMIZED, "anonymize", anonymize, anonymized, null),
                Arguments.of(State.ANONYMIZED, "anonymize with the account", withAccount, anonymized, null));
    }

    @ParameterizedTest(name = "{0} driver, {1}")
    @MethodSource("lifecycle")
    void GivenADriverInEachState_WhenEachCommandRuns_ThenItReachesTheAllowedStateOrRefusesWithTheNamedError(
            State state, String name, Command command, DriverStatus reached, DomainError error) {
        Driver driver = inState(state);

        if (error != null) {
            assertRefusedUnchanged(driver, () -> command.run(driver), error);
        } else {
            command.run(driver);
            assertThat(driver.snapshot().status()).isEqualTo(reached);
        }
    }

    // --- restore

    static Stream<DriverBuilder> builderStates() {
        return Stream.of(
                aDriver(),
                aDriver()
                        .withWeeklyShifts(
                                WeeklyShifts.of(Map.of(DayOfWeek.SUNDAY, MORNING, DayOfWeek.FRIDAY, MORNING))),
                aDriver().retired(),
                aDriver().anonymized(),
                aDriver()
                        .withStatus(new DriverStatus.Anonymized(Optional.empty(), NOW))
                        .withContact(Optional.empty()));
    }

    @ParameterizedTest
    @MethodSource("builderStates")
    void GivenEachBuilderState_WhenItsSnapshotIsRestored_ThenTheSnapshotRoundTrips(DriverBuilder builder) {
        Driver driver = builder.build();

        assertThat(Driver.restore(driver.snapshot()).snapshot()).isEqualTo(builder.snapshot());
        assertThat(driver.version()).isEqualTo(LOADED);
        assertThat(driver.pendingEvents()).isEmpty();
    }

    private static final String CONTACT_UNTIL_ANONYMIZED =
            "a driver has a name and phone exactly until they are anonymized";

    static Stream<Arguments> damagedSnapshots() {
        return Stream.of(
                Arguments.of(
                        "an active driver without a name and phone",
                        aDriver().withContact(Optional.empty()),
                        CONTACT_UNTIL_ANONYMIZED),
                Arguments.of(
                        "a retired driver without a name and phone",
                        aDriver().retired().withContact(Optional.empty()),
                        CONTACT_UNTIL_ANONYMIZED),
                Arguments.of(
                        "an anonymized driver who still has a name and phone",
                        aDriver().anonymized().withContact(Optional.of(DANA_CONTACT)),
                        CONTACT_UNTIL_ANONYMIZED),
                Arguments.of(
                        "anonymized before being retired",
                        aDriver()
                                .anonymized()
                                .withStatus(new DriverStatus.Anonymized(
                                        Optional.of(RETIRED_AT), RETIRED_AT.minusSeconds(1))),
                        "a driver is anonymized only after being retired"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedSnapshots")
    void GivenASnapshotBreakingAnInvariantAtRest_WhenRestored_ThenStoredStateInvalid(
            String broken, DriverBuilder builder, String detail) {
        assertThatThrownBy(builder::build).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
            assertThat(invalid.subject()).isEqualTo("Driver");
            assertThat(invalid.detail()).isEqualTo(detail);
        });
    }

    @Test
    void GivenADriverJustRetired_WhenTheirEventAndSnapshotArePrinted_ThenNeitherNamesThemOrTheirPhone() {
        Driver driver = aDriver().build();
        driver.retire(PlanUses.none(), TODAY, NOW);

        assertThat(driver.pendingEvents().toString()).doesNotContain("Dana").doesNotContain("345");
        assertThat(driver.snapshot().toString()).doesNotContain("Dana").doesNotContain("345");
    }
}
