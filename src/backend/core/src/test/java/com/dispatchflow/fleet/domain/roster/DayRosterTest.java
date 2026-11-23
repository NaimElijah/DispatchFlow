package com.dispatchflow.fleet.domain.roster;

import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.DANA;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.DAY_SHIFT;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.FALL_BACK;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.LOADED;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.NOA;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.OMER;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.ORDINARY_DAY;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.ROSTER_ID;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.SPRING_FORWARD;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.V1;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.V2;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.V3;
import static com.dispatchflow.fleet.domain.roster.DayRosterBuilder.aRoster;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.fleet.domain.FleetError.DriverAlreadyHasVehicle;
import com.dispatchflow.fleet.domain.FleetError.DriverMarkedUnavailable;
import com.dispatchflow.fleet.domain.FleetError.DriverNotRostered;
import com.dispatchflow.fleet.domain.FleetError.NotAnActiveDriver;
import com.dispatchflow.fleet.domain.FleetError.VehicleAlreadyAssigned;
import com.dispatchflow.fleet.domain.FleetError.VehicleNotActive;
import com.dispatchflow.fleet.domain.RosterDefault;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.fleet.domain.driver.Driver;
import com.dispatchflow.fleet.domain.driver.DriverBuilder;
import com.dispatchflow.fleet.domain.driver.WeeklyShifts;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One date's roster (DL §4.2): who works, when, and with which vehicle (FR-54, FR-190), its
 * defaults (FR-79, FR-189, ADR-103), availability (FR-07, FR-82, FR-300) and hand-over (FR-191,
 * FR-302).
 */
class DayRosterTest {

    private static final ShiftHours EARLY = ShiftHours.of(LocalTime.of(6, 0), LocalTime.of(14, 0));
    private static final ShiftHours LATE = ShiftHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));
    private static final List<VehicleId> ALL_ACTIVE = List.of(V1, V2, V3);

    private static void assertRefusedUnchanged(DayRoster roster, ThrowingCallable call, DomainError expected) {
        DayRosterSnapshot before = roster.snapshot();

        assertRefused(call, expected);

        assertThat(roster.snapshot()).isEqualTo(before);
        assertThat(roster.pendingEvents()).isEmpty();
    }

    private static RosterEntry entry(
            AccountId driver, ShiftHours hours, RosterVehicle vehicle, Availability availability) {
        return new RosterEntry(driver, Shift.on(ORDINARY_DAY, hours), vehicle, availability);
    }

    private static RosterDefault activeDefault(AccountId driver, ShiftHours hours, VehicleId vehicle) {
        return RosterDefault.working(driver, hours, vehicle, Standing.ACTIVE);
    }

    private static RosterVehicle drives(VehicleId vehicle) {
        return new RosterVehicle.Assigned(vehicle);
    }

    private static RosterVehicle vehicleOf(DayRoster roster, AccountId driver) {
        return roster.entryOf(driver).orElseThrow().vehicle();
    }

    // --- opening and filling (FR-79, FR-189, ADR-103)

    @Test
    void GivenADate_WhenItsRosterIsOpened_ThenItIsNewAndEmpty() {
        DayRoster roster = DayRoster.open(ROSTER_ID, ORDINARY_DAY);

        assertThat(roster.date()).isEqualTo(ORDINARY_DAY);
        assertThat(roster.snapshot().version()).isNull();
        assertThat(roster.snapshot()).isEqualTo(new DayRosterSnapshot(ORDINARY_DAY, null, List.of(), List.of()));
        assertThat(roster.pendingEvents()).isEmpty();
    }

    @Test
    void GivenDefaults_WhenAnEmptyRosterIsFilled_ThenEachDriverGetsTheirShiftAndVehicle() {
        DayRoster roster = aRoster().build();

        RosterFill fill =
                roster.fill(List.of(activeDefault(DANA, DAY_SHIFT, V1), activeDefault(OMER, EARLY, V2)), ALL_ACTIVE);

        assertThat(roster.snapshot().entries())
                .containsExactly(
                        entry(DANA, DAY_SHIFT, drives(V1), Availability.AVAILABLE),
                        entry(OMER, EARLY, drives(V2), Availability.AVAILABLE));
        assertThat(fill).isEqualTo(new RosterFill(List.of(DANA, OMER), List.of()));
    }

    @Test
    void GivenADispatcherSetADriversShift_WhenTheRosterIsFilled_ThenTheyGetTheDefaultVehicleAndKeepTheirShift() {
        DayRoster roster = DayRoster.open(ROSTER_ID, ORDINARY_DAY);
        roster.changeShift(DANA, LATE, Standing.ACTIVE);

        RosterFill fill = roster.fill(List.of(activeDefault(DANA, DAY_SHIFT, V1)), ALL_ACTIVE);

        assertThat(roster.entryOf(DANA)).contains(entry(DANA, LATE, drives(V1), Availability.AVAILABLE));
        assertThat(fill.filled()).containsExactly(DANA);
    }

    @Test
    void GivenAMondayOnlyTemplateAndATuesdayShiftSetByHand_WhenTheRosterIsFilled_ThenTheDriverGetsTheDefaultVehicle() {
        Driver dana = DriverBuilder.aDriver()
                .withWeeklyShifts(WeeklyShifts.of(Map.of(DayOfWeek.MONDAY, EARLY)))
                .build();
        DayRoster tuesday = DayRoster.open(ROSTER_ID, ORDINARY_DAY);
        tuesday.changeShift(DANA, LATE, Standing.ACTIVE);

        RosterFill fill = tuesday.fill(
                dana.rosterDefaultOn(ORDINARY_DAY, Standing.ACTIVE).stream().toList(), ALL_ACTIVE);

        assertThat(tuesday.entryOf(DANA))
                .contains(entry(DANA, LATE, drives(DriverBuilder.DEFAULT_VEHICLE), Availability.AVAILABLE));
        assertThat(fill).isEqualTo(new RosterFill(List.of(DANA), List.of()));
    }

    @Test
    void GivenADayOffDefaultForADriverNotOnTheRoster_WhenTheRosterIsFilled_ThenTheyAreNotAddedAndNothingIsReported() {
        DayRoster roster = aRoster().build();

        RosterFill fill = roster.fill(
                List.of(
                        RosterDefault.dayOff(DANA, V1, Standing.ACTIVE),
                        RosterDefault.dayOff(OMER, V2, Standing.NOT_ACTIVE)),
                ALL_ACTIVE);

        assertThat(roster.snapshot().entries()).isEmpty();
        assertThat(fill).isEqualTo(new RosterFill(List.of(), List.of()));
    }

    @Test
    void GivenADispatcherChoseNoVehicleForADriver_WhenTheRosterIsFilled_ThenTheyStayWithoutOne() {
        DayRoster roster = aRoster().withDriverWithoutVehicle(DANA).build();

        RosterFill fill = roster.fill(List.of(activeDefault(DANA, EARLY, V1)), ALL_ACTIVE);

        assertThat(roster.snapshot())
                .isEqualTo(aRoster().withDriverWithoutVehicle(DANA).snapshot());
        assertThat(fill).isEqualTo(new RosterFill(List.of(), List.of()));
    }

    @Test
    void GivenADispatcherGaveADriverAVehicle_WhenTheRosterIsFilled_ThenTheyKeepIt() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V2).build();

        roster.fill(List.of(activeDefault(DANA, EARLY, V1)), ALL_ACTIVE);

        assertThat(roster.snapshot())
                .isEqualTo(aRoster().withDriverDriving(DANA, V2).snapshot());
    }

    @Test
    void
            GivenAnUnavailableDriverWithNoVehicleSet_WhenTheRosterIsFilled_ThenTheyGetTheDefaultVehicleAndStayUnavailable() {
        DayRoster roster = aRoster().withUnavailableDriver(DANA).build();

        roster.fill(List.of(activeDefault(DANA, EARLY, V1)), ALL_ACTIVE);

        assertThat(roster.entryOf(DANA)).contains(entry(DANA, DAY_SHIFT, drives(V1), Availability.UNAVAILABLE));
    }

    @Test
    void GivenAFilledRoster_WhenFilledAgain_ThenNothingChangesAndNothingIsReported() {
        DayRoster roster = aRoster().build();
        List<RosterDefault> defaults = List.of(activeDefault(DANA, DAY_SHIFT, V1));
        roster.fill(defaults, ALL_ACTIVE);
        DayRosterSnapshot once = roster.snapshot();

        RosterFill again = roster.fill(defaults, ALL_ACTIVE);

        assertThat(roster.snapshot()).isEqualTo(once);
        assertThat(again).isEqualTo(new RosterFill(List.of(), List.of()));
    }

    @Test
    void GivenTwoDefaultsForOneDriver_WhenTheRosterIsFilled_ThenTheFirstIsKept() {
        DayRoster roster = aRoster().build();

        RosterFill fill =
                roster.fill(List.of(activeDefault(DANA, DAY_SHIFT, V1), activeDefault(DANA, EARLY, V2)), ALL_ACTIVE);

        assertThat(roster.snapshot().entries())
                .containsExactly(entry(DANA, DAY_SHIFT, drives(V1), Availability.AVAILABLE));
        assertThat(fill).isEqualTo(new RosterFill(List.of(DANA), List.of()));
    }

    @Test
    void GivenTwoDefaultsForOneVehicle_WhenTheRosterIsFilled_ThenTheSecondDriverIsSkippedWithVehicleAlreadyAssigned() {
        DayRoster roster = aRoster().build();

        RosterFill fill =
                roster.fill(List.of(activeDefault(DANA, DAY_SHIFT, V1), activeDefault(OMER, EARLY, V1)), ALL_ACTIVE);

        assertThat(roster.snapshot().entries())
                .containsExactly(entry(DANA, DAY_SHIFT, drives(V1), Availability.AVAILABLE));
        assertThat(fill)
                .isEqualTo(new RosterFill(
                        List.of(DANA), List.of(new SkippedDriver(OMER, new VehicleAlreadyAssigned(V1, DANA)))));
    }

    @Test
    void GivenADefaultVehicleSomeoneAlreadyDrives_WhenTheRosterIsFilled_ThenThatDriverIsSkippedAndTheOthersFilled() {
        DayRoster roster = aRoster().withDriverDriving(NOA, V1).build();

        RosterFill fill =
                roster.fill(List.of(activeDefault(DANA, DAY_SHIFT, V1), activeDefault(OMER, EARLY, V2)), ALL_ACTIVE);

        assertThat(roster.entryOf(DANA)).isEmpty();
        assertThat(vehicleOf(roster, OMER)).isEqualTo(drives(V2));
        assertThat(fill.skipped()).containsExactly(new SkippedDriver(DANA, new VehicleAlreadyAssigned(V1, NOA)));
    }

    @Test
    void GivenADriverWhoseShiftIsSetAndWhoseDefaultVehicleIsTaken_WhenTheRosterIsFilled_ThenTheirEntryStaysAsItWas() {
        DayRoster roster = aRoster().withDriverDriving(NOA, V1).withDriver(DANA).build();

        RosterFill fill = roster.fill(List.of(activeDefault(DANA, EARLY, V1)), ALL_ACTIVE);

        assertThat(roster.snapshot())
                .isEqualTo(aRoster().withDriverDriving(NOA, V1).withDriver(DANA).snapshot());
        assertThat(fill.skipped()).containsExactly(new SkippedDriver(DANA, new VehicleAlreadyAssigned(V1, NOA)));
    }

    @Test
    void GivenARetiredOrUnknownDefaultVehicle_WhenTheRosterIsFilled_ThenThatDriverIsSkippedWithVehicleNotActive() {
        DayRoster roster = aRoster().build();

        RosterFill fill =
                roster.fill(List.of(activeDefault(DANA, DAY_SHIFT, V1), activeDefault(OMER, EARLY, V2)), List.of(V2));

        assertThat(roster.entryOf(DANA)).isEmpty();
        assertThat(fill)
                .isEqualTo(new RosterFill(List.of(OMER), List.of(new SkippedDriver(DANA, new VehicleNotActive(V1)))));
    }

    @Test
    void
            GivenADefaultShiftInTheSkippedHour_WhenTheSpringForwardRosterIsFilled_ThenThatDriverIsSkippedWithNonexistentLocalTime() {
        DayRoster roster = aRoster().on(SPRING_FORWARD).build();
        ShiftHours twoThirty = ShiftHours.of(LocalTime.of(2, 30), LocalTime.of(10, 0));

        RosterFill fill = roster.fill(
                List.of(activeDefault(DANA, twoThirty, V1), activeDefault(OMER, DAY_SHIFT, V2)), ALL_ACTIVE);

        assertThat(roster.entryOf(DANA)).isEmpty();
        assertThat(fill)
                .isEqualTo(new RosterFill(
                        List.of(OMER),
                        List.of(new SkippedDriver(
                                DANA,
                                new NonexistentLocalTime(SPRING_FORWARD.value().atTime(2, 30))))));
    }

    @Test
    void GivenADriverWhoseAccountIsNotAnActiveDriver_WhenTheRosterIsFilled_ThenTheyAreSkippedWithNotAnActiveDriver() {
        DayRoster roster = aRoster().withDriver(DANA).build();
        List<RosterDefault> defaults = List.of(
                RosterDefault.working(DANA, DAY_SHIFT, V1, Standing.NOT_ACTIVE),
                RosterDefault.working(OMER, EARLY, V2, Standing.NOT_ACTIVE),
                activeDefault(NOA, EARLY, V3));

        RosterFill fill = roster.fill(defaults, ALL_ACTIVE);

        assertThat(vehicleOf(roster, DANA)).isEqualTo(new RosterVehicle.NotSet());
        assertThat(roster.entryOf(OMER)).isEmpty();
        assertThat(fill)
                .isEqualTo(new RosterFill(
                        List.of(NOA),
                        List.of(
                                new SkippedDriver(DANA, new NotAnActiveDriver(DANA)),
                                new SkippedDriver(OMER, new NotAnActiveDriver(OMER)))));
    }

    // --- shifts (FR-54, FR-190)

    @Test
    void GivenAnActiveDriverNotYetRostered_WhenTheirShiftIsSet_ThenTheyWorkThatShiftWithTheirVehicleNotSet() {
        DayRoster roster = aRoster().build();

        roster.changeShift(DANA, EARLY, Standing.ACTIVE);

        assertThat(roster.entryOf(DANA))
                .contains(entry(DANA, EARLY, new RosterVehicle.NotSet(), Availability.AVAILABLE));
        assertThat(roster.entryOf(OMER)).isEmpty();
    }

    @Test
    void GivenARosteredDriver_WhenTheirShiftChanges_ThenTheyKeepTheirVehicle() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V1).build();

        roster.changeShift(DANA, EARLY, Standing.ACTIVE);

        assertThat(roster.entryOf(DANA)).contains(entry(DANA, EARLY, drives(V1), Availability.AVAILABLE));
    }

    @Test
    void GivenAnUnavailableDriver_WhenTheirShiftChanges_ThenTheyStayUnavailable() {
        DayRoster roster = aRoster().withUnavailableDriver(DANA).build();

        roster.changeShift(DANA, EARLY, Standing.ACTIVE);

        assertThat(roster.entryOf(DANA))
                .contains(entry(DANA, EARLY, new RosterVehicle.NotSet(), Availability.UNAVAILABLE));
    }

    @Test
    void GivenADriverWhoIsNotActive_WhenTheirShiftIsSet_ThenNotAnActiveDriver() {
        DayRoster roster = aRoster().build();

        assertRefusedUnchanged(
                roster, () -> roster.changeShift(DANA, EARLY, Standing.NOT_ACTIVE), new NotAnActiveDriver(DANA));
    }

    @Test
    void GivenAShiftStartingInTheSkippedHour_WhenSetOn26March2027_ThenNonexistentLocalTime() {
        DayRoster roster = aRoster().on(SPRING_FORWARD).withDriver(DANA).build();

        assertRefusedUnchanged(
                roster,
                () -> roster.changeShift(DANA, ShiftHours.of(LocalTime.of(2, 0), LocalTime.of(9, 0)), Standing.ACTIVE),
                new NonexistentLocalTime(SPRING_FORWARD.value().atTime(2, 0)));
    }

    @Test
    void GivenAShiftStartingInTheRepeatedHour_WhenSetOn31October2027_ThenItStartsAtTheFirstOccurrence() {
        DayRoster roster = aRoster().on(FALL_BACK).build();

        roster.changeShift(DANA, ShiftHours.of(LocalTime.of(1, 30), LocalTime.of(9, 0)), Standing.ACTIVE);

        Shift shift = roster.entryOf(DANA).orElseThrow().shift();
        assertThat(shift.date()).isEqualTo(FALL_BACK);
        assertThat(shift.startInDay().seconds()).isEqualTo(5_400);
        assertThat(shift.endInDay().seconds()).isEqualTo(36_000);
    }

    // --- vehicles (DL §4.2)

    @Test
    void GivenARosteredDriverAndAFreeVehicle_WhenAssigned_ThenTheDriverDrivesIt() {
        DayRoster roster = aRoster().withDriver(DANA).build();

        roster.assign(DANA, V1, Standing.ACTIVE, Standing.ACTIVE);

        assertThat(vehicleOf(roster, DANA)).isEqualTo(drives(V1));
    }

    @Test
    void GivenAVehicleAnotherDriverDrives_WhenAssigned_ThenVehicleAlreadyAssigned() {
        DayRoster roster =
                aRoster().withDriverDriving(OMER, V1).withDriver(DANA).build();

        assertRefusedUnchanged(
                roster,
                () -> roster.assign(DANA, V1, Standing.ACTIVE, Standing.ACTIVE),
                new VehicleAlreadyAssigned(V1, OMER));
    }

    @Test
    void GivenADriverWhoDrivesAnotherVehicle_WhenAssigned_ThenDriverAlreadyHasVehicle() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V2).build();

        assertRefusedUnchanged(
                roster,
                () -> roster.assign(DANA, V1, Standing.ACTIVE, Standing.ACTIVE),
                new DriverAlreadyHasVehicle(DANA, V2));
    }

    @Test
    void GivenADriverAlreadyDrivingTheVehicle_WhenAssignedItAgain_ThenNothingChanges() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V1).build();

        roster.assign(DANA, V1, Standing.ACTIVE, Standing.ACTIVE);

        assertThat(roster.snapshot())
                .isEqualTo(aRoster().withDriverDriving(DANA, V1).snapshot());
    }

    @Test
    void GivenARetiredVehicle_WhenAssigned_ThenVehicleNotActive() {
        DayRoster roster = aRoster().withDriver(DANA).build();

        assertRefusedUnchanged(
                roster, () -> roster.assign(DANA, V1, Standing.ACTIVE, Standing.NOT_ACTIVE), new VehicleNotActive(V1));
    }

    @Test
    void GivenADriverWhoIsNoLongerActive_WhenAssigned_ThenNotAnActiveDriver() {
        DayRoster roster = aRoster().withDriver(DANA).build();

        assertRefusedUnchanged(
                roster,
                () -> roster.assign(DANA, V1, Standing.NOT_ACTIVE, Standing.ACTIVE),
                new NotAnActiveDriver(DANA));
    }

    interface Command {
        void run(DayRoster roster);
    }

    static Stream<Arguments> commandsNeedingARosteredDriver() {
        return Stream.of(
                Arguments.of("assign", (Command) roster -> roster.assign(DANA, V1, Standing.ACTIVE, Standing.ACTIVE)),
                Arguments.of("unassign", (Command) roster -> roster.unassign(DANA)),
                Arguments.of("mark unavailable", (Command) roster -> roster.markDriverUnavailable(DANA)),
                Arguments.of("mark available", (Command) roster -> roster.markDriverAvailable(DANA)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandsNeedingARosteredDriver")
    void GivenADriverWithNoShiftOnTheDate_WhenACommandNeedsTheirEntry_ThenDriverNotRostered(
            String name, Command command) {
        DayRoster roster = aRoster().build();

        assertRefusedUnchanged(roster, () -> command.run(roster), new DriverNotRostered(DANA));
    }

    @Test
    void GivenADriverWithAVehicle_WhenUnassigned_ThenTheyWorkWithNoVehicleAsAChoiceMade() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V1).build();

        roster.unassign(DANA);

        assertThat(roster.entryOf(DANA))
                .contains(entry(DANA, DAY_SHIFT, new RosterVehicle.NoVehicle(), Availability.AVAILABLE));
    }

    // --- availability (FR-07, FR-82, FR-300, FR-301)

    @Test
    void GivenAnAvailableDriverWithAVehicle_WhenMarkedUnavailable_ThenTheyAreUnavailableAndKeepTheVehicle() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V1).build();

        roster.markDriverUnavailable(DANA);

        assertThat(roster.entryOf(DANA)).contains(entry(DANA, DAY_SHIFT, drives(V1), Availability.UNAVAILABLE));
    }

    @Test
    void GivenAnUnavailableDriver_WhenMarkedAvailableAgain_ThenTheyAreAvailable() {
        DayRoster roster = aRoster().withUnavailableDriver(DANA).build();

        roster.markDriverAvailable(DANA);

        assertThat(roster.entryOf(DANA).orElseThrow().availability()).isEqualTo(Availability.AVAILABLE);
    }

    @Test
    void GivenActiveVehicles_WhenMarkedUnavailableOnceOrTwice_ThenEachIsListedOnceAndKeepsItsDriver() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V1).build();

        roster.markVehicleUnavailable(V1, Standing.ACTIVE);
        roster.markVehicleUnavailable(V3, Standing.ACTIVE);
        roster.markVehicleUnavailable(V1, Standing.ACTIVE);

        assertThat(roster.snapshot().unavailableVehicles()).containsExactly(V1, V3);
        assertThat(roster.isUnavailable(V1)).isTrue();
        assertThat(vehicleOf(roster, DANA)).isEqualTo(drives(V1));
    }

    @Test
    void GivenAnUnavailableVehicle_WhenMarkedAvailableAgain_ThenItIsNoLongerListed() {
        DayRoster roster =
                aRoster().withUnavailableVehicle(V1).withUnavailableVehicle(V3).build();

        roster.markVehicleAvailable(V1);

        assertThat(roster.snapshot().unavailableVehicles()).containsExactly(V3);
        assertThat(roster.isUnavailable(V1)).isFalse();
    }

    @Test
    void GivenNoVehicle_WhenMarkedAvailableOrAskedAbout_ThenTheMissingArgumentIsNamed() {
        DayRoster roster = aRoster().withUnavailableVehicle(V1).build();

        assertThatThrownBy(() -> roster.markVehicleAvailable(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("vehicle");
        assertThatThrownBy(() -> roster.isUnavailable(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("vehicle");
        assertThat(roster.snapshot())
                .isEqualTo(aRoster().withUnavailableVehicle(V1).snapshot());
    }

    @Test
    void GivenAVehicleTheRegisterDoesNotHoldAsActive_WhenMarkedUnavailable_ThenVehicleNotActive() {
        DayRoster roster = aRoster().build();

        assertRefusedUnchanged(
                roster, () -> roster.markVehicleUnavailable(V1, Standing.NOT_ACTIVE), new VehicleNotActive(V1));
    }

    // --- hand-over (FR-191, FR-302)

    @Test
    void GivenAFreeAvailableDriver_WhenAskedIfTheyCanTakeAVehicle_ThenTheyCanWithTheirShift() {
        DayRoster roster =
                aRoster().withDriverDriving(DANA, V1).withDriver(OMER).build();

        Assignability answer = roster.checkAssignable(V1, OMER, Standing.ACTIVE);

        assertThat(answer).isEqualTo(new Assignability.Assignable(Shift.on(ORDINARY_DAY, DAY_SHIFT)));
        assertThat(answer.orRefuse()).isEqualTo(Shift.on(ORDINARY_DAY, DAY_SHIFT));
    }

    @Test
    void GivenTheDriverWhoAlreadyDrivesTheVehicle_WhenAskedIfTheyCanTakeIt_ThenTheyCan() {
        DayRoster roster = aRoster().withDriverDriving(OMER, V1).build();

        assertThat(roster.checkAssignable(V1, OMER, Standing.ACTIVE))
                .isEqualTo(new Assignability.Assignable(Shift.on(ORDINARY_DAY, DAY_SHIFT)));
    }

    @Test
    void GivenADriverMarkedUnavailable_WhenAskedIfTheyCanTakeAVehicle_ThenDriverMarkedUnavailable() {
        DayRoster roster = aRoster()
                .withDriverDriving(DANA, V1)
                .withUnavailableDriver(OMER)
                .build();

        Assignability answer = roster.checkAssignable(V1, OMER, Standing.ACTIVE);

        assertThat(answer).isEqualTo(new Assignability.NotAssignable(new DriverMarkedUnavailable(OMER)));
        assertRefusedUnchanged(roster, answer::orRefuse, new DriverMarkedUnavailable(OMER));
    }

    @Test
    void GivenARetiredDriverStillOnAFilledRoster_WhenAskedIfTheyCanTakeAVehicle_ThenNotAnActiveDriver() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V1).build();
        roster.fill(List.of(activeDefault(OMER, DAY_SHIFT, V2)), ALL_ACTIVE);

        assertRefused(
                () -> roster.checkAssignable(V1, OMER, Standing.NOT_ACTIVE).orRefuse(), new NotAnActiveDriver(OMER));
    }

    @Test
    void GivenADriverNotOnTheRoster_WhenAskedIfTheyCanTakeAVehicle_ThenDriverNotRostered() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V1).build();

        assertRefused(() -> roster.checkAssignable(V1, OMER, Standing.ACTIVE).orRefuse(), new DriverNotRostered(OMER));
    }

    @Test
    void GivenADriverWhoDrivesAnotherVehicle_WhenAskedIfTheyCanTakeOne_ThenDriverAlreadyHasVehicle() {
        DayRoster roster = aRoster()
                .withDriverDriving(DANA, V1)
                .withDriverDriving(OMER, V2)
                .build();

        assertRefused(
                () -> roster.checkAssignable(V1, OMER, Standing.ACTIVE).orRefuse(),
                new DriverAlreadyHasVehicle(OMER, V2));
    }

    @Test
    void GivenAHandedOverRoute_WhenItsVehicleMovesWithIt_ThenTheNewDriverDrivesItAndTheOldOneHasNone() {
        DayRoster roster =
                aRoster().withDriverDriving(DANA, V1).withDriver(OMER).build();

        roster.moveVehicleWithRoute(V1, DANA, OMER);

        assertThat(vehicleOf(roster, DANA)).isEqualTo(new RosterVehicle.NoVehicle());
        assertThat(vehicleOf(roster, OMER)).isEqualTo(drives(V1));
        assertThat(roster.pendingEvents()).isEmpty();
    }

    @Test
    void GivenTheVehicleMovedAwayWithARoute_WhenTheRosterIsFilled_ThenTheOldDriverIsNotGivenItBack() {
        DayRoster roster =
                aRoster().withDriverDriving(DANA, V1).withDriver(OMER).build();
        roster.moveVehicleWithRoute(V1, DANA, OMER);

        RosterFill fill = roster.fill(List.of(activeDefault(DANA, DAY_SHIFT, V1)), ALL_ACTIVE);

        assertThat(vehicleOf(roster, DANA)).isEqualTo(new RosterVehicle.NoVehicle());
        assertThat(fill).isEqualTo(new RosterFill(List.of(), List.of()));
    }

    @Test
    void GivenTheMoveAlreadyMade_WhenTheSameEventIsDeliveredAgain_ThenNothingChanges() {
        DayRoster roster =
                aRoster().withDriver(DANA).withDriverDriving(OMER, V1).build();

        roster.moveVehicleWithRoute(V1, DANA, OMER);

        assertThat(roster.snapshot())
                .isEqualTo(
                        aRoster().withDriver(DANA).withDriverDriving(OMER, V1).snapshot());
    }

    @Test
    void GivenAVehicleNoOneDrivesOnTheDate_WhenItMovesWithARoute_ThenTheNewDriverDrivesIt() {
        DayRoster roster = aRoster().withDriver(DANA).withDriver(OMER).build();

        roster.moveVehicleWithRoute(V1, DANA, OMER);

        assertThat(vehicleOf(roster, OMER)).isEqualTo(drives(V1));
        assertThat(vehicleOf(roster, DANA)).isEqualTo(new RosterVehicle.NotSet());
    }

    @Test
    void GivenTheNewDriverMarkedUnavailableSinceTheHandOver_WhenTheVehicleMoves_ThenTheRosterStillFollowsThePlan() {
        DayRoster roster = aRoster()
                .withDriverDriving(DANA, V1)
                .withUnavailableDriver(OMER)
                .build();

        roster.moveVehicleWithRoute(V1, DANA, OMER);

        assertThat(vehicleOf(roster, OMER)).isEqualTo(drives(V1));
    }

    @Test
    void GivenAThirdDriverNowDrivesTheVehicle_WhenItMovesWithARoute_ThenVehicleAlreadyAssigned() {
        DayRoster roster = aRoster()
                .withDriver(DANA)
                .withDriverDriving(NOA, V1)
                .withDriver(OMER)
                .build();

        assertRefusedUnchanged(
                roster, () -> roster.moveVehicleWithRoute(V1, DANA, OMER), new VehicleAlreadyAssigned(V1, NOA));
    }

    @Test
    void GivenTheNewDriverDrivesAnotherVehicle_WhenAVehicleMovesToThem_ThenDriverAlreadyHasVehicle() {
        DayRoster roster = aRoster()
                .withDriverDriving(DANA, V1)
                .withDriverDriving(OMER, V2)
                .build();

        assertRefusedUnchanged(
                roster, () -> roster.moveVehicleWithRoute(V1, DANA, OMER), new DriverAlreadyHasVehicle(OMER, V2));
    }

    @Test
    void GivenTheNewDriverNotOnTheRoster_WhenAVehicleMovesToThem_ThenDriverNotRostered() {
        DayRoster roster = aRoster().withDriverDriving(DANA, V1).build();

        assertRefusedUnchanged(roster, () -> roster.moveVehicleWithRoute(V1, DANA, OMER), new DriverNotRostered(OMER));
    }

    @Test
    void GivenAMissingArgument_WhenAVehicleMovesWithARouteOrAnEntryIsAskedFor_ThenTheMissingArgumentIsNamed() {
        DayRoster roster =
                aRoster().withDriverDriving(DANA, V1).withDriver(OMER).build();

        assertThatThrownBy(() -> roster.moveVehicleWithRoute(null, DANA, OMER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("vehicle");
        assertThatThrownBy(() -> roster.moveVehicleWithRoute(V1, null, OMER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("from");
        assertThatThrownBy(() -> roster.moveVehicleWithRoute(V1, DANA, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("to");
        assertThatThrownBy(() -> roster.entryOf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("driver");
        assertThat(roster.snapshot())
                .isEqualTo(
                        aRoster().withDriverDriving(DANA, V1).withDriver(OMER).snapshot());
    }

    // --- the lifecycle of one driver's entry

    enum EntryState {
        NOT_ROSTERED,
        AVAILABLE_WITHOUT_VEHICLE,
        AVAILABLE_WITH_VEHICLE,
        UNAVAILABLE
    }

    /** The allowed result of a command, as a check on the roster, or the named error. */
    record Expected(
            @Nullable Consumer<DayRoster> check, @Nullable DomainError error) {

        static Expected accepted(Consumer<DayRoster> check) {
            return new Expected(check, null);
        }

        static Expected refused(DomainError error) {
            return new Expected(null, error);
        }
    }

    private static DayRoster withOmerIn(EntryState state) {
        DayRosterBuilder builder = aRoster().withDriverDriving(DANA, V1);
        return switch (state) {
            case NOT_ROSTERED -> builder.build();
            case AVAILABLE_WITHOUT_VEHICLE -> builder.withDriver(OMER).build();
            case AVAILABLE_WITH_VEHICLE -> builder.withDriverDriving(OMER, V2).build();
            case UNAVAILABLE -> builder.withUnavailableDriver(OMER).build();
        };
    }

    private static Consumer<DayRoster> omerIs(ShiftHours hours, RosterVehicle vehicle, Availability availability) {
        return roster -> assertThat(roster.entryOf(OMER)).contains(entry(OMER, hours, vehicle, availability));
    }

    static Stream<Arguments> lifecycle() {
        Command changeShift = roster -> roster.changeShift(OMER, EARLY, Standing.ACTIVE);
        Command assign = roster -> roster.assign(OMER, V3, Standing.ACTIVE, Standing.ACTIVE);
        Command unassign = roster -> roster.unassign(OMER);
        Command unavailable = roster -> roster.markDriverUnavailable(OMER);
        Command available = roster -> roster.markDriverAvailable(OMER);
        Command handOver = roster -> roster.moveVehicleWithRoute(V1, DANA, OMER);
        Command check =
                roster -> roster.checkAssignable(V1, OMER, Standing.ACTIVE).orRefuse();
        RosterVehicle notSet = new RosterVehicle.NotSet();
        RosterVehicle none = new RosterVehicle.NoVehicle();
        Availability up = Availability.AVAILABLE;
        Availability down = Availability.UNAVAILABLE;
        Expected notRostered = Expected.refused(new DriverNotRostered(OMER));
        Expected hasV2 = Expected.refused(new DriverAlreadyHasVehicle(OMER, V2));
        Expected unchangedFree = Expected.accepted(omerIs(DAY_SHIFT, notSet, up));
        return Stream.of(
                Arguments.of(
                        EntryState.NOT_ROSTERED,
                        "change shift",
                        changeShift,
                        Expected.accepted(omerIs(EARLY, notSet, up))),
                Arguments.of(EntryState.NOT_ROSTERED, "assign", assign, notRostered),
                Arguments.of(EntryState.NOT_ROSTERED, "unassign", unassign, notRostered),
                Arguments.of(EntryState.NOT_ROSTERED, "mark unavailable", unavailable, notRostered),
                Arguments.of(EntryState.NOT_ROSTERED, "mark available", available, notRostered),
                Arguments.of(EntryState.NOT_ROSTERED, "hand over", handOver, notRostered),
                Arguments.of(EntryState.NOT_ROSTERED, "check assignable", check, notRostered),
                Arguments.of(
                        EntryState.AVAILABLE_WITHOUT_VEHICLE,
                        "change shift",
                        changeShift,
                        Expected.accepted(omerIs(EARLY, notSet, up))),
                Arguments.of(
                        EntryState.AVAILABLE_WITHOUT_VEHICLE,
                        "assign",
                        assign,
                        Expected.accepted(omerIs(DAY_SHIFT, drives(V3), up))),
                Arguments.of(
                        EntryState.AVAILABLE_WITHOUT_VEHICLE,
                        "unassign",
                        unassign,
                        Expected.accepted(omerIs(DAY_SHIFT, none, up))),
                Arguments.of(
                        EntryState.AVAILABLE_WITHOUT_VEHICLE,
                        "mark unavailable",
                        unavailable,
                        Expected.accepted(omerIs(DAY_SHIFT, notSet, down))),
                Arguments.of(EntryState.AVAILABLE_WITHOUT_VEHICLE, "mark available", available, unchangedFree),
                Arguments.of(
                        EntryState.AVAILABLE_WITHOUT_VEHICLE,
                        "hand over",
                        handOver,
                        Expected.accepted(omerIs(DAY_SHIFT, drives(V1), up))),
                Arguments.of(EntryState.AVAILABLE_WITHOUT_VEHICLE, "check assignable", check, unchangedFree),
                Arguments.of(
                        EntryState.AVAILABLE_WITH_VEHICLE,
                        "change shift",
                        changeShift,
                        Expected.accepted(omerIs(EARLY, drives(V2), up))),
                Arguments.of(EntryState.AVAILABLE_WITH_VEHICLE, "assign", assign, hasV2),
                Arguments.of(
                        EntryState.AVAILABLE_WITH_VEHICLE,
                        "unassign",
                        unassign,
                        Expected.accepted(omerIs(DAY_SHIFT, none, up))),
                Arguments.of(
                        EntryState.AVAILABLE_WITH_VEHICLE,
                        "mark unavailable",
                        unavailable,
                        Expected.accepted(omerIs(DAY_SHIFT, drives(V2), down))),
                Arguments.of(
                        EntryState.AVAILABLE_WITH_VEHICLE,
                        "mark available",
                        available,
                        Expected.accepted(omerIs(DAY_SHIFT, drives(V2), up))),
                Arguments.of(EntryState.AVAILABLE_WITH_VEHICLE, "hand over", handOver, hasV2),
                Arguments.of(EntryState.AVAILABLE_WITH_VEHICLE, "check assignable", check, hasV2),
                Arguments.of(
                        EntryState.UNAVAILABLE,
                        "change shift",
                        changeShift,
                        Expected.accepted(omerIs(EARLY, notSet, down))),
                Arguments.of(
                        EntryState.UNAVAILABLE,
                        "assign",
                        assign,
                        Expected.accepted(omerIs(DAY_SHIFT, drives(V3), down))),
                Arguments.of(
                        EntryState.UNAVAILABLE, "unassign", unassign, Expected.accepted(omerIs(DAY_SHIFT, none, down))),
                Arguments.of(
                        EntryState.UNAVAILABLE,
                        "mark unavailable",
                        unavailable,
                        Expected.accepted(omerIs(DAY_SHIFT, notSet, down))),
                Arguments.of(
                        EntryState.UNAVAILABLE,
                        "mark available",
                        available,
                        Expected.accepted(omerIs(DAY_SHIFT, notSet, up))),
                Arguments.of(
                        EntryState.UNAVAILABLE,
                        "hand over",
                        handOver,
                        Expected.accepted(omerIs(DAY_SHIFT, drives(V1), down))),
                Arguments.of(
                        EntryState.UNAVAILABLE,
                        "check assignable",
                        check,
                        Expected.refused(new DriverMarkedUnavailable(OMER))));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("lifecycle")
    void GivenADriversEntryInEachState_WhenEachCommandRuns_ThenItReachesTheAllowedResultOrRefusesWithTheNamedError(
            EntryState state, String name, Command command, Expected expected) {
        DayRoster roster = withOmerIn(state);

        if (expected.error() != null) {
            assertRefusedUnchanged(roster, () -> command.run(roster), expected.error());
        } else {
            command.run(roster);
            Objects.requireNonNull(expected.check()).accept(roster);
            assertThat(roster.pendingEvents()).isEmpty();
        }
    }

    // --- restore

    static Stream<DayRosterBuilder> builderStates() {
        return Stream.of(
                aRoster(),
                aRoster().withDriverDriving(DANA, V1).withDriver(OMER).withUnavailableDriver(NOA),
                aRoster().withDriverWithoutVehicle(DANA).withDriverDriving(OMER, V2),
                aRoster().withDriverDriving(DANA, V1).withUnavailableVehicle(V1).withUnavailableVehicle(V3),
                aRoster().on(SPRING_FORWARD).withDriverDriving(DANA, V1),
                aRoster().on(FALL_BACK).withDriver(DANA));
    }

    @ParameterizedTest
    @MethodSource("builderStates")
    void GivenEachBuilderState_WhenItsSnapshotIsRestored_ThenTheSnapshotRoundTrips(DayRosterBuilder builder) {
        DayRoster roster = builder.build();

        assertThat(DayRoster.restore(roster.snapshot()).snapshot()).isEqualTo(builder.snapshot());
        assertThat(roster.version()).isEqualTo(LOADED);
    }

    static Stream<Arguments> damagedSnapshots() {
        RosterEntry onAnotherDate = new RosterEntry(
                DANA, Shift.on(FALL_BACK, DAY_SHIFT), new RosterVehicle.NotSet(), Availability.AVAILABLE);
        return Stream.of(
                Arguments.of(
                        "a driver appears twice", aRoster().withDriver(DANA).withDriverDriving(DANA, V1)),
                Arguments.of(
                        "a vehicle has two drivers",
                        aRoster().withDriverDriving(DANA, V1).withDriverDriving(OMER, V1)),
                Arguments.of("a shift lies on another date", aRoster().withEntry(onAnotherDate)),
                Arguments.of(
                        "a vehicle is marked unavailable twice",
                        aRoster().withUnavailableVehicle(V1).withUnavailableVehicle(V1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedSnapshots")
    void GivenASnapshotBreakingAnInvariantAtRest_WhenRestored_ThenStoredStateInvalid(
            String detail, DayRosterBuilder builder) {
        assertThatThrownBy(builder::build).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
            assertThat(invalid.subject()).isEqualTo("DayRoster");
            assertThat(invalid.detail()).isEqualTo(detail);
        });
    }
}
