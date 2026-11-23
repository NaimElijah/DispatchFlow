package com.dispatchflow.fleet.domain.register;

import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.DAY_HOURS;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.EAST;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.NORTH;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.NORTH_POINT;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.RETIRED_AT;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.SOUTH;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.VAN_CAPACITY;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.aRegister;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.aRegisterWithNoDepots;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.activeDepot;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.activeVehicle;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.details;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.retiredVehicle;
import static com.dispatchflow.fleet.domain.register.FleetRegisterBuilder.vehicleId;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.fleet.domain.FleetError.DepotHasActiveVehicles;
import com.dispatchflow.fleet.domain.FleetError.DepotInUse;
import com.dispatchflow.fleet.domain.FleetError.DuplicatePlate;
import com.dispatchflow.fleet.domain.FleetError.DuplicateVehicleName;
import com.dispatchflow.fleet.domain.FleetError.FleetFull;
import com.dispatchflow.fleet.domain.FleetError.UnknownDepot;
import com.dispatchflow.fleet.domain.FleetError.UnknownVehicle;
import com.dispatchflow.fleet.domain.FleetError.VehicleInUse;
import com.dispatchflow.fleet.domain.FleetError.VehicleNotActive;
import com.dispatchflow.fleet.domain.PlanUse;
import com.dispatchflow.fleet.domain.PlanUses;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The fleet register's invariants (DL §4.2): BR-36, BR-40, BR-43, BR-48, FR-197, FR-305, FR-307. */
class FleetRegisterTest {

    private static final Instant NOW = Instant.parse("2027-06-15T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 15);
    private static final PlanId PLAN = PlanId.of(UUID.fromString("0190a5e2-0000-7000-a000-000000000001"));
    private static final VehicleId NEW_VEHICLE = vehicleId(99);

    /** Asserts the refusal, and that the register's state and pending events did not change. */
    private static void assertRefusedUnchanged(FleetRegister register, ThrowingCallable call, DomainError expected) {
        RegisterDescription before = RegisterDescription.of(register);

        assertRefused(call, expected);

        assertThat(RegisterDescription.of(register)).isEqualTo(before);
        assertThat(register.pendingEvents()).isEmpty();
    }

    private static VehicleDescription vehicleIn(FleetRegister register, VehicleId id) {
        return RegisterDescription.of(register).vehicles().stream()
                .filter(vehicle -> vehicle.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static DepotDescription depotIn(FleetRegister register, DepotId id) {
        return RegisterDescription.of(register).depots().stream()
                .filter(depot -> depot.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static PlanUses usedOn(LocalDate date) {
        return PlanUses.of(List.of(PlanUse.uncancelled(PLAN, DeliveryDate.of(date))));
    }

    // --- creation and depots

    @Test
    void GivenNothing_WhenAnEmptyRegisterIsCreated_ThenItIsNewWithNoDepotsOrVehiclesAndNoEvent() {
        FleetRegister register = FleetRegister.empty();

        assertThat(register.savedVersion()).isEmpty();
        assertThat(RegisterDescription.of(register).depots()).isEmpty();
        assertThat(RegisterDescription.of(register).vehicles()).isEmpty();
        assertThat(register.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADepotWithCoordinatesAndHours_WhenAdded_ThenItIsActiveWithBoth() {
        FleetRegister register = aRegisterWithNoDepots().build();

        register.addDepot(NORTH, DepotName.of("North"), Optional.of(NORTH_POINT), Optional.of(DAY_HOURS));

        assertThat(RegisterDescription.of(register).depots()).containsExactly(activeDepot(NORTH, "North"));
        assertThat(register.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADepotWithoutCoordinates_WhenAdded_ThenItIsKeptWithoutThem() {
        FleetRegister register = aRegisterWithNoDepots().build();

        register.addDepot(NORTH, DepotName.of("North"), Optional.empty(), Optional.empty());

        DepotDescription depot = depotIn(register, NORTH);
        assertThat(depot.location()).isEmpty();
        assertThat(depot.hours()).isEmpty();
        assertThat(depot.retiredAt()).isEmpty();
    }

    @Test
    void GivenADepotIdAlreadyInTheRegister_WhenAddedAgain_ThenItFailsAsAProgrammingError() {
        FleetRegister register = aRegister().build();

        assertThatThrownBy(() -> register.addDepot(NORTH, DepotName.of("Again"), Optional.empty(), Optional.empty()))
                .isInstanceOf(AssertionError.class)
                .hasMessage("a new depot needs a new ID");
        assertThat(RegisterDescription.of(register)).isEqualTo(aRegister().description());
        assertThat(register.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADepotWithoutCoordinates_WhenItsLocationIsSet_ThenItHasThem() {
        FleetRegister register = aRegisterWithNoDepots()
                .withDepot(new DepotDescription(
                        NORTH, DepotName.of("North"), Optional.empty(), Optional.empty(), Optional.empty()))
                .build();

        register.changeDepot(NORTH, DepotName.of("North hub"), Optional.of(NORTH_POINT));

        assertThat(depotIn(register, NORTH).name()).isEqualTo(DepotName.of("North hub"));
        assertThat(depotIn(register, NORTH).location()).contains(NORTH_POINT);
    }

    @Test
    void GivenADepotWithCoordinates_WhenTheyAreCleared_ThenItHasNone() {
        FleetRegister register = aRegister().build();

        register.changeDepot(NORTH, DepotName.of("North"), Optional.empty());

        assertThat(depotIn(register, NORTH).location()).isEmpty();
        assertThat(depotIn(register, NORTH).hours()).contains(DAY_HOURS);
    }

    @Test
    void GivenADepot_WhenItsHoursAreChanged_ThenItHasTheNewHours() {
        FleetRegister register = aRegister().build();
        DepotHours later = DepotHours.of(LocalTime.of(7, 0), LocalTime.of(21, 0));

        register.changeDepotHours(NORTH, Optional.of(later));

        assertThat(depotIn(register, NORTH).hours()).contains(later);
    }

    @Test
    void GivenADepotWithHours_WhenTheyAreCleared_ThenItHasNone() {
        FleetRegister register = aRegister().build();

        register.changeDepotHours(NORTH, Optional.empty());

        assertThat(depotIn(register, NORTH).hours()).isEmpty();
    }

    static Stream<Arguments> depotCommands() {
        return Stream.of(
                Arguments.of("change", (Command)
                        register -> register.changeDepot(EAST, DepotName.of("East"), Optional.empty())),
                Arguments.of("change hours", (Command) register -> register.changeDepotHours(EAST, Optional.empty())),
                Arguments.of("retire", (Command) register -> register.retireDepot(EAST, PlanUses.none(), TODAY, NOW)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("depotCommands")
    void GivenADepotIdNotInTheRegister_WhenACommandNamesIt_ThenUnknownDepot(String name, Command command) {
        FleetRegister register = aRegister().build();

        assertRefusedUnchanged(register, () -> command.run(register), new UnknownDepot(EAST));
    }

    @Test
    void GivenADepotWithNoVehiclesAndNoPlan_WhenRetired_ThenItIsRetiredAtNow() {
        FleetRegister register = aRegister().build();

        register.retireDepot(SOUTH, PlanUses.none(), TODAY, NOW);

        assertThat(depotIn(register, SOUTH).retiredAt()).contains(NOW);
        assertThat(register.pendingEvents()).isEmpty();
    }

    @Test
    void GivenActiveVehiclesAtADepot_WhenTheDepotIsRetired_ThenDepotHasActiveVehicles() {
        FleetRegister register = aRegister()
                .withVehicle(activeVehicle(1))
                .withVehicle(new VehicleDescription(
                        vehicleId(2),
                        new VehicleDetails(VehicleName.of("Vehicle 2"), Plate.of("P2"), VAN_CAPACITY, SOUTH, NORTH),
                        Optional.empty()))
                .withVehicle(activeVehicle(3))
                .build();

        assertRefusedUnchanged(
                register,
                () -> register.retireDepot(NORTH, PlanUses.none(), TODAY, NOW),
                new DepotHasActiveVehicles(NORTH, List.of(vehicleId(1), vehicleId(2), vehicleId(3))));
    }

    @Test
    void GivenOnlyRetiredVehiclesAtADepot_WhenTheDepotIsRetired_ThenItIsRetired() {
        FleetRegister register = aRegister().withVehicle(retiredVehicle(1)).build();

        register.retireDepot(NORTH, PlanUses.none(), TODAY, NOW);

        assertThat(depotIn(register, NORTH).retiredAt()).contains(NOW);
    }

    @Test
    void GivenAnUncancelledPlanForTodayUsesADepot_WhenRetired_ThenDepotInUse() {
        FleetRegister register = aRegister().build();

        assertRefusedUnchanged(
                register,
                () -> register.retireDepot(SOUTH, usedOn(TODAY), TODAY, NOW),
                new DepotInUse(SOUTH, PLAN, DeliveryDate.of(TODAY)));
    }

    @Test
    void GivenOnlyAPastPlanAndACancelledOneUseADepot_WhenRetired_ThenItIsRetired() {
        FleetRegister register = aRegister().build();
        PlanUses uses = PlanUses.of(List.of(
                PlanUse.uncancelled(PLAN, DeliveryDate.of(TODAY.minusDays(1))),
                PlanUse.cancelled(PLAN, DeliveryDate.of(TODAY))));

        register.retireDepot(SOUTH, uses, TODAY, NOW);

        assertThat(depotIn(register, SOUTH).retiredAt()).contains(NOW);
    }

    // --- adding vehicles: BR-36, BR-40, BR-48

    @Test
    void GivenRoomInTheFleet_WhenAVehicleIsAdded_ThenItIsActiveWithItsDetails() {
        FleetRegister register = aRegister().build();

        register.addVehicle(NEW_VEHICLE, details(99));

        assertThat(vehicleIn(register, NEW_VEHICLE))
                .isEqualTo(new VehicleDescription(NEW_VEHICLE, details(99), Optional.empty()));
        assertThat(register.standingOf(NEW_VEHICLE)).isEqualTo(Standing.ACTIVE);
        assertThat(register.activeVehicles()).containsExactly(NEW_VEHICLE);
        assertThat(register.pendingEvents()).isEmpty();
    }

    @Test
    void GivenFourteenActiveVehicles_WhenTheFifteenthIsAdded_ThenItIsAccepted() {
        FleetRegister register = aRegister().withActiveVehicles(14).build();

        register.addVehicle(NEW_VEHICLE, details(99));

        assertThat(register.activeVehicles()).hasSize(FleetRegister.MAX_ACTIVE_VEHICLES);
    }

    @Test
    void GivenFifteenActiveVehicles_WhenASixteenthIsAdded_ThenFleetFull() {
        FleetRegister register = aRegister().withActiveVehicles(15).build();

        assertRefusedUnchanged(register, () -> register.addVehicle(NEW_VEHICLE, details(99)), new FleetFull(15));
    }

    @Test
    void GivenFourteenActiveVehiclesAndRetiredOnes_WhenTheFifteenthIsAdded_ThenRetiredOnesDoNotCount() {
        FleetRegister register = aRegister()
                .withActiveVehicles(14)
                .withVehicle(retiredVehicle(20))
                .withVehicle(retiredVehicle(21))
                .build();

        register.addVehicle(NEW_VEHICLE, details(99));

        assertThat(register.activeVehicles()).hasSize(15).contains(NEW_VEHICLE);
    }

    @Test
    void GivenAnActiveVehicleWithAName_WhenAnotherIsAddedWithThatName_ThenDuplicateVehicleName() {
        FleetRegister register = aRegister().withActiveVehicles(1).build();
        VehicleDetails sameName =
                new VehicleDetails(VehicleName.of(" Vehicle 1 "), Plate.of("OTHER"), VAN_CAPACITY, NORTH, NORTH);

        assertRefusedUnchanged(
                register, () -> register.addVehicle(NEW_VEHICLE, sameName), new DuplicateVehicleName(vehicleId(1)));
    }

    @Test
    void GivenAnActiveVehicleNamedVan1_WhenAnotherIsAddedAsVan1WithANoBreakSpace_ThenDuplicateVehicleName() {
        FleetRegister register = aRegister()
                .withVehicle(new VehicleDescription(
                        vehicleId(1),
                        new VehicleDetails(VehicleName.of("Van 1"), Plate.of("P1"), VAN_CAPACITY, NORTH, NORTH),
                        Optional.empty()))
                .build();
        VehicleDetails sameName =
                new VehicleDetails(VehicleName.of("Van 1\u00a0"), Plate.of("P2"), VAN_CAPACITY, NORTH, NORTH);

        assertRefusedUnchanged(
                register, () -> register.addVehicle(NEW_VEHICLE, sameName), new DuplicateVehicleName(vehicleId(1)));
    }

    /**
     * Pairs of names that look alike, so BR-48 matches them as one name: each is a name the kernel
     * keeps as entered, which only Fleet's matching form folds. Every character outside ASCII and
     * the Hebrew letters is built from its code point, so none of them hides in the source.
     */
    static Stream<Arguments> lookalikeNames() {
        String truck = cp(0x1F69A);
        String heart = cp(0x2764);
        String precomposedE = "Van " + cp(0x00E9);
        String alefWithMadda = "Van " + cp(0x0622);
        String geresh = cp(0x05F3);
        String gershayim = cp(0x05F4);
        return Stream.of(
                Arguments.of("a left-to-right mark after Van", "Van " + truck, "Van" + cp(0x200E) + " " + truck),
                Arguments.of("a no-break space for the space", "Van " + truck, "Van" + cp(0x00A0) + truck),
                Arguments.of("an emoji selector after the heart", "Van " + heart, "Van " + heart + cp(0xFE0F)),
                Arguments.of("two spaces for one", "Van 1", "Van  1"),
                Arguments.of("a hair space for the space", "Van 1", "Van" + cp(0x200A) + "1"),
                Arguments.of("a non-joiner inside a decomposed é", precomposedE, "Van e" + cp(0x200C) + cp(0x0301)),
                Arguments.of("a joiner inside a decomposed é", precomposedE, "Van e" + cp(0x200D) + cp(0x0301)),
                Arguments.of(
                        "a left-to-right mark inside a decomposed é", precomposedE, "Van e" + cp(0x200E) + cp(0x0301)),
                Arguments.of(
                        "an Arabic letter mark inside a decomposed alef with madda",
                        alefWithMadda,
                        "Van " + cp(0x0627) + cp(0x061C) + cp(0x0653)),
                Arguments.of("an apostrophe for the geresh", "ג" + geresh + "יפ 1", "ג'יפ 1"),
                Arguments.of("a left single quote for the geresh", "ג" + geresh + "יפ 1", "ג" + cp(0x2018) + "יפ 1"),
                Arguments.of("a right single quote for the geresh", "ג" + geresh + "יפ 1", "ג" + cp(0x2019) + "יפ 1"),
                Arguments.of("a backtick for the geresh", "ג" + geresh + "יפ 1", "ג`יפ 1"),
                Arguments.of("a double quote for the gershayim", "צה" + gershayim + "ל 1", "צה\"ל 1"),
                Arguments.of("two apostrophes for the gershayim", "צה" + gershayim + "ל 1", "צה''ל 1"),
                Arguments.of(
                        "two gereshes for the gershayim", "צה" + gershayim + "ל 1", "צה" + geresh + geresh + "ל 1"),
                Arguments.of(
                        "a left double quote for the gershayim", "צה" + gershayim + "ל 1", "צה" + cp(0x201C) + "ל 1"),
                Arguments.of(
                        "a right double quote for the gershayim", "צה" + gershayim + "ל 1", "צה" + cp(0x201D) + "ל 1"),
                Arguments.of("a hyphen for the hyphen-minus", "Van-1", "Van" + cp(0x2010) + "1"),
                Arguments.of("a non-breaking hyphen for the hyphen-minus", "Van-1", "Van" + cp(0x2011) + "1"),
                Arguments.of("a figure dash for the hyphen-minus", "Van-1", "Van" + cp(0x2012) + "1"),
                Arguments.of("an en dash for the hyphen-minus", "Van-1", "Van" + cp(0x2013) + "1"),
                Arguments.of("an em dash for the hyphen-minus", "Van-1", "Van" + cp(0x2014) + "1"),
                Arguments.of("a horizontal bar for the hyphen-minus", "Van-1", "Van" + cp(0x2015) + "1"),
                Arguments.of("a minus sign for the hyphen-minus", "Van-1", "Van" + cp(0x2212) + "1"),
                Arguments.of("a small hyphen-minus for the hyphen-minus", "Van-1", "Van" + cp(0xFE63) + "1"),
                Arguments.of("a fullwidth hyphen-minus for the hyphen-minus", "Van-1", "Van" + cp(0xFF0D) + "1"));
    }

    private static String cp(int codePoint) {
        return Character.toString(codePoint);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lookalikeNames")
    void GivenAnActiveVehicle_WhenAnotherIsAddedWithALookalikeName_ThenDuplicateVehicleName(
            String lookalike, String existing, String name) {
        FleetRegister register = aRegister()
                .withVehicle(new VehicleDescription(
                        vehicleId(1),
                        new VehicleDetails(VehicleName.of(existing), Plate.of("P1"), VAN_CAPACITY, NORTH, NORTH),
                        Optional.empty()))
                .build();
        VehicleDetails sameName = new VehicleDetails(VehicleName.of(name), Plate.of("P2"), VAN_CAPACITY, NORTH, NORTH);

        assertRefusedUnchanged(
                register, () -> register.addVehicle(NEW_VEHICLE, sameName), new DuplicateVehicleName(vehicleId(1)));
    }

    @Test
    void GivenANameWithALeftToRightMarkAndNoClash_WhenAdded_ThenTheVehicleKeepsTheNameAsEntered() {
        FleetRegister register = aRegister().build();
        String entered = "Van" + Character.toString(0x200E) + " 1";

        register.addVehicle(
                NEW_VEHICLE, new VehicleDetails(VehicleName.of(entered), Plate.of("P2"), VAN_CAPACITY, NORTH, NORTH));

        assertThat(vehicleIn(register, NEW_VEHICLE).details().name().value()).isEqualTo(entered);
    }

    @Test
    void
            GivenAnActiveVehicleNamedVan1_WhenAnotherIsAddedAsLowerCaseVan1_ThenItIsAcceptedBecauseNamesAreCaseSensitive() {
        FleetRegister register = aRegister()
                .withVehicle(new VehicleDescription(
                        vehicleId(1),
                        new VehicleDetails(VehicleName.of("Van 1"), Plate.of("P1"), VAN_CAPACITY, NORTH, NORTH),
                        Optional.empty()))
                .build();

        register.addVehicle(
                NEW_VEHICLE, new VehicleDetails(VehicleName.of("van 1"), Plate.of("P2"), VAN_CAPACITY, NORTH, NORTH));

        assertThat(register.standingOf(NEW_VEHICLE)).isEqualTo(Standing.ACTIVE);
    }

    @Test
    void GivenARetiredVehicleWithAName_WhenAnotherIsAddedWithThatNameAndPlate_ThenItIsAccepted() {
        FleetRegister register = aRegister().withVehicle(retiredVehicle(1)).build();

        register.addVehicle(NEW_VEHICLE, details(1));

        assertThat(register.standingOf(NEW_VEHICLE)).isEqualTo(Standing.ACTIVE);
    }

    @Test
    void GivenAnActiveVehicleWithAPlate_WhenAnotherIsAddedWithThatPlateWrittenDifferently_ThenDuplicatePlate() {
        FleetRegister register = aRegister()
                .withVehicle(new VehicleDescription(
                        vehicleId(1),
                        new VehicleDetails(VehicleName.of("Truck"), Plate.of("1234567"), VAN_CAPACITY, NORTH, NORTH),
                        Optional.empty()))
                .build();
        VehicleDetails samePlate =
                new VehicleDetails(VehicleName.of("Van"), Plate.of("12-345-67"), VAN_CAPACITY, NORTH, NORTH);

        assertRefusedUnchanged(
                register, () -> register.addVehicle(NEW_VEHICLE, samePlate), new DuplicatePlate(vehicleId(1)));
    }

    @Test
    void GivenADepotNotInTheRegister_WhenAVehicleStartsThere_ThenUnknownDepot() {
        FleetRegister register = aRegister().build();
        VehicleDetails fromEast = new VehicleDetails(VehicleName.of("Van"), Plate.of("P99"), VAN_CAPACITY, EAST, NORTH);

        assertRefusedUnchanged(register, () -> register.addVehicle(NEW_VEHICLE, fromEast), new UnknownDepot(EAST));
    }

    @Test
    void GivenARetiredDepot_WhenAVehicleEndsThere_ThenUnknownDepot() {
        FleetRegister register = aRegister().withRetiredDepot(EAST, "East").build();
        VehicleDetails toEast = new VehicleDetails(VehicleName.of("Van"), Plate.of("P99"), VAN_CAPACITY, NORTH, EAST);

        assertRefusedUnchanged(register, () -> register.addVehicle(NEW_VEHICLE, toEast), new UnknownDepot(EAST));
    }

    @Test
    void GivenAVehicleIdAlreadyInTheRegister_WhenAddedAgain_ThenItFailsAsAProgrammingError() {
        FleetRegister register = aRegister().withVehicle(retiredVehicle(1)).build();

        assertThatThrownBy(() -> register.addVehicle(vehicleId(1), details(2)))
                .isInstanceOf(AssertionError.class)
                .hasMessage("a new vehicle needs a new ID");
        assertThat(RegisterDescription.of(register))
                .isEqualTo(aRegister().withVehicle(retiredVehicle(1)).description());
        assertThat(register.pendingEvents()).isEmpty();
    }

    // --- changing vehicles

    @Test
    void GivenAnActiveVehicle_WhenChangedKeepingItsOwnNameAndPlate_ThenTheNewDetailsApply() {
        FleetRegister register = aRegister().withActiveVehicles(2).build();
        VehicleDetails moved =
                new VehicleDetails(VehicleName.of("Vehicle 1"), Plate.of("P1"), VAN_CAPACITY, SOUTH, SOUTH);

        register.changeVehicle(vehicleId(1), moved);

        assertThat(vehicleIn(register, vehicleId(1)).details()).isEqualTo(moved);
    }

    @Test
    void GivenTwoActiveVehicles_WhenOneTakesTheOthersName_ThenDuplicateVehicleName() {
        FleetRegister register = aRegister().withActiveVehicles(2).build();
        VehicleDetails clash =
                new VehicleDetails(VehicleName.of("Vehicle 2"), Plate.of("P1"), VAN_CAPACITY, NORTH, NORTH);

        assertRefusedUnchanged(
                register, () -> register.changeVehicle(vehicleId(1), clash), new DuplicateVehicleName(vehicleId(2)));
    }

    @Test
    void GivenTwoActiveVehicles_WhenOneTakesTheOthersPlate_ThenDuplicatePlate() {
        FleetRegister register = aRegister().withActiveVehicles(2).build();
        VehicleDetails clash =
                new VehicleDetails(VehicleName.of("Vehicle 1"), Plate.of("p-2"), VAN_CAPACITY, NORTH, NORTH);

        assertRefusedUnchanged(
                register, () -> register.changeVehicle(vehicleId(1), clash), new DuplicatePlate(vehicleId(2)));
    }

    static Stream<Arguments> vehicleCommands() {
        return Stream.of(
                Arguments.of("change", (Command) register -> register.changeVehicle(NEW_VEHICLE, details(99))),
                Arguments.of("retire", (Command)
                        register -> register.retireVehicle(NEW_VEHICLE, PlanUses.none(), TODAY, NOW)),
                Arguments.of("reactivate", (Command) register -> register.reactivateVehicle(NEW_VEHICLE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vehicleCommands")
    void GivenAVehicleIdNotInTheRegister_WhenACommandNamesIt_ThenUnknownVehicle(String name, Command command) {
        FleetRegister register = aRegister().withActiveVehicles(1).build();

        assertRefusedUnchanged(register, () -> command.run(register), new UnknownVehicle(NEW_VEHICLE));
    }

    @Test
    void GivenAVehicleIdNotInTheRegister_WhenItsStandingIsAsked_ThenItIsNotActive() {
        assertThat(aRegister().build().standingOf(NEW_VEHICLE)).isEqualTo(Standing.NOT_ACTIVE);
    }

    @Test
    void GivenARetiredVehicle_WhenChanged_ThenVehicleNotActive() {
        FleetRegister register = aRegister().withVehicle(retiredVehicle(1)).build();

        assertRefusedUnchanged(
                register, () -> register.changeVehicle(vehicleId(1), details(1)), new VehicleNotActive(vehicleId(1)));
    }

    @Test
    void GivenAnActiveVehicle_WhenMovedToARetiredDepot_ThenUnknownDepot() {
        FleetRegister register =
                aRegister().withRetiredDepot(EAST, "East").withActiveVehicles(1).build();
        VehicleDetails toEast =
                new VehicleDetails(VehicleName.of("Vehicle 1"), Plate.of("P1"), VAN_CAPACITY, EAST, EAST);

        assertRefusedUnchanged(register, () -> register.changeVehicle(vehicleId(1), toEast), new UnknownDepot(EAST));
    }

    // --- retiring and reactivating vehicles: BR-43, FR-305, FR-307

    @Test
    void GivenAnActiveVehicleNoPlanUses_WhenRetired_ThenItIsRetiredAtNowAndNoLongerActive() {
        FleetRegister register = aRegister().withActiveVehicles(2).build();

        register.retireVehicle(vehicleId(1), PlanUses.none(), TODAY, NOW);

        assertThat(vehicleIn(register, vehicleId(1)).retiredAt()).contains(NOW);
        assertThat(register.standingOf(vehicleId(1))).isEqualTo(Standing.NOT_ACTIVE);
        assertThat(register.activeVehicles()).containsExactly(vehicleId(2));
        assertThat(register.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAnUncancelledPlanForTodayUsesAVehicle_WhenRetired_ThenVehicleInUse() {
        FleetRegister register = aRegister().withActiveVehicles(1).build();

        assertRefusedUnchanged(
                register,
                () -> register.retireVehicle(vehicleId(1), usedOn(TODAY), TODAY, NOW),
                new VehicleInUse(vehicleId(1), PLAN, DeliveryDate.of(TODAY)));
    }

    @Test
    void GivenSeveralPlansUseAVehicle_WhenRetired_ThenTheEarliestFromTodayIsNamedInVehicleInUse() {
        FleetRegister register = aRegister().withActiveVehicles(1).build();
        PlanId nextWeek = PlanId.of(UUID.fromString("0190a5e2-0000-7000-a000-000000000002"));
        PlanId tomorrow = PlanId.of(UUID.fromString("0190a5e2-0000-7000-a000-000000000003"));
        PlanUses uses = PlanUses.of(List.of(
                PlanUse.uncancelled(nextWeek, DeliveryDate.of(TODAY.plusDays(7))),
                PlanUse.uncancelled(PLAN, DeliveryDate.of(TODAY.minusDays(1))),
                PlanUse.cancelled(PLAN, DeliveryDate.of(TODAY)),
                PlanUse.uncancelled(tomorrow, DeliveryDate.of(TODAY.plusDays(1)))));

        assertRefusedUnchanged(
                register,
                () -> register.retireVehicle(vehicleId(1), uses, TODAY, NOW),
                new VehicleInUse(vehicleId(1), tomorrow, DeliveryDate.of(TODAY.plusDays(1))));
    }

    @Test
    void GivenOnlyAPastPlanAndACancelledOneUseAVehicle_WhenRetired_ThenItIsRetired() {
        FleetRegister register = aRegister().withActiveVehicles(1).build();
        PlanUses uses = PlanUses.of(List.of(
                PlanUse.uncancelled(PLAN, DeliveryDate.of(TODAY.minusDays(1))),
                PlanUse.cancelled(PLAN, DeliveryDate.of(TODAY.plusDays(2)))));

        register.retireVehicle(vehicleId(1), uses, TODAY, NOW);

        assertThat(register.standingOf(vehicleId(1))).isEqualTo(Standing.NOT_ACTIVE);
    }

    @Test
    void GivenARetiredVehicle_WhenRetiredAgain_ThenVehicleNotActive() {
        FleetRegister register = aRegister().withVehicle(retiredVehicle(1)).build();

        assertRefusedUnchanged(
                register,
                () -> register.retireVehicle(vehicleId(1), PlanUses.none(), TODAY, NOW),
                new VehicleNotActive(vehicleId(1)));
    }

    @Test
    void GivenARetiredVehicleAndRoom_WhenReactivated_ThenItIsActiveAgain() {
        FleetRegister register = aRegister().withVehicle(retiredVehicle(1)).build();

        register.reactivateVehicle(vehicleId(1));

        assertThat(vehicleIn(register, vehicleId(1)).retiredAt()).isEmpty();
        assertThat(register.standingOf(vehicleId(1))).isEqualTo(Standing.ACTIVE);
    }

    @Test
    void GivenFifteenActiveVehicles_WhenARetiredOneIsReactivated_ThenFleetFull() {
        FleetRegister register = aRegister()
                .withActiveVehicles(15)
                .withVehicle(retiredVehicle(20))
                .build();

        assertRefusedUnchanged(register, () -> register.reactivateVehicle(vehicleId(20)), new FleetFull(15));
    }

    @Test
    void GivenARetiredVehicleWhosePlateIsNowActive_WhenReactivated_ThenDuplicatePlate() {
        FleetRegister register = aRegister()
                .withVehicle(retiredVehicle(1))
                .withVehicle(new VehicleDescription(
                        vehicleId(2),
                        new VehicleDetails(VehicleName.of("New van"), Plate.of("P1"), VAN_CAPACITY, NORTH, NORTH),
                        Optional.empty()))
                .build();

        assertRefusedUnchanged(
                register, () -> register.reactivateVehicle(vehicleId(1)), new DuplicatePlate(vehicleId(2)));
    }

    @Test
    void GivenARetiredVehicleWhoseNameIsNowActive_WhenReactivated_ThenDuplicateVehicleName() {
        FleetRegister register = aRegister()
                .withVehicle(retiredVehicle(1))
                .withVehicle(new VehicleDescription(
                        vehicleId(2),
                        new VehicleDetails(VehicleName.of("Vehicle 1"), Plate.of("NEW1"), VAN_CAPACITY, NORTH, NORTH),
                        Optional.empty()))
                .build();

        assertRefusedUnchanged(
                register, () -> register.reactivateVehicle(vehicleId(1)), new DuplicateVehicleName(vehicleId(2)));
    }

    @Test
    void GivenARetiredVehicleWhoseDepotIsRetired_WhenReactivated_ThenUnknownDepot() {
        FleetRegister register = aRegister()
                .withRetiredDepot(EAST, "East")
                .withVehicle(new VehicleDescription(
                        vehicleId(1),
                        new VehicleDetails(VehicleName.of("Vehicle 1"), Plate.of("P1"), VAN_CAPACITY, NORTH, EAST),
                        Optional.of(RETIRED_AT)))
                .build();

        assertRefusedUnchanged(register, () -> register.reactivateVehicle(vehicleId(1)), new UnknownDepot(EAST));
    }

    @Test
    void GivenAnActiveVehicle_WhenReactivated_ThenNothingChanges() {
        FleetRegister register = aRegister().withActiveVehicles(1).build();

        register.reactivateVehicle(vehicleId(1));

        assertThat(RegisterDescription.of(register))
                .isEqualTo(aRegister().withActiveVehicles(1).description());
    }

    @Test
    void GivenFifteenActiveVehicles_WhenAnActiveOneIsReactivated_ThenNothingChangesAndTheFleetIsNotFull() {
        FleetRegister register = aRegister().withActiveVehicles(15).build();

        register.reactivateVehicle(vehicleId(1));

        assertThat(RegisterDescription.of(register))
                .isEqualTo(aRegister().withActiveVehicles(15).description());
    }

    // --- the lifecycle of a vehicle and of a depot

    enum RecordState {
        ACTIVE,
        RETIRED
    }

    /** The allowed result of a command, or the named error it must refuse with. */
    record Expected(
            @Nullable Consumer<FleetRegister> check,
            @Nullable DomainError error) {

        static Expected accepted(Consumer<FleetRegister> check) {
            return new Expected(check, null);
        }

        static Expected refused(DomainError error) {
            return new Expected(null, error);
        }
    }

    interface Command {
        void run(FleetRegister register);
    }

    private static FleetRegister withVehicleIn(RecordState state) {
        return aRegister()
                .withVehicle(state == RecordState.ACTIVE ? activeVehicle(1) : retiredVehicle(1))
                .build();
    }

    private static FleetRegister withDepotIn(RecordState state) {
        FleetRegisterBuilder builder = aRegister();
        return (state == RecordState.ACTIVE
                        ? builder.withDepot(activeDepot(EAST, "East"))
                        : builder.withRetiredDepot(EAST, "East"))
                .build();
    }

    static Stream<Arguments> vehicleLifecycle() {
        VehicleId one = vehicleId(1);
        Command change = register -> register.changeVehicle(one, details(1));
        Command retire = register -> register.retireVehicle(one, PlanUses.none(), TODAY, NOW);
        Command reactivate = register -> register.reactivateVehicle(one);
        return Stream.of(
                Arguments.of(
                        RecordState.ACTIVE,
                        "change",
                        change,
                        Expected.accepted(r -> assertThat(r.standingOf(one)).isEqualTo(Standing.ACTIVE))),
                Arguments.of(
                        RecordState.ACTIVE,
                        "retire",
                        retire,
                        Expected.accepted(r -> assertThat(r.standingOf(one)).isEqualTo(Standing.NOT_ACTIVE))),
                Arguments.of(
                        RecordState.ACTIVE,
                        "reactivate",
                        reactivate,
                        Expected.accepted(r -> assertThat(r.standingOf(one)).isEqualTo(Standing.ACTIVE))),
                Arguments.of(RecordState.RETIRED, "change", change, Expected.refused(new VehicleNotActive(one))),
                Arguments.of(RecordState.RETIRED, "retire", retire, Expected.refused(new VehicleNotActive(one))),
                Arguments.of(
                        RecordState.RETIRED,
                        "reactivate",
                        reactivate,
                        Expected.accepted(r -> assertThat(r.standingOf(one)).isEqualTo(Standing.ACTIVE))));
    }

    @ParameterizedTest(name = "{0} vehicle, {1}")
    @MethodSource("vehicleLifecycle")
    void GivenAVehicleInEachState_WhenEachCommandRuns_ThenItTakesTheAllowedResultOrRefusesWithTheNamedError(
            RecordState state, String name, Command command, Expected expected) {
        FleetRegister register = withVehicleIn(state);
        runAndCheck(register, command, expected);
    }

    static Stream<Arguments> depotLifecycle() {
        Command change = register -> register.changeDepot(EAST, DepotName.of("East hub"), Optional.empty());
        Command hours = register -> register.changeDepotHours(EAST, Optional.empty());
        Command retire = register -> register.retireDepot(EAST, PlanUses.none(), TODAY, NOW);
        Command addVehicleThere = register -> register.addVehicle(
                NEW_VEHICLE, new VehicleDetails(VehicleName.of("Van"), Plate.of("P99"), VAN_CAPACITY, EAST, EAST));
        Consumer<FleetRegister> stillActive =
                r -> assertThat(depotIn(r, EAST).retiredAt()).isEmpty();
        return Stream.of(
                Arguments.of(RecordState.ACTIVE, "change", change, Expected.accepted(stillActive)),
                Arguments.of(RecordState.ACTIVE, "change hours", hours, Expected.accepted(stillActive)),
                Arguments.of(
                        RecordState.ACTIVE,
                        "retire",
                        retire,
                        Expected.accepted(
                                r -> assertThat(depotIn(r, EAST).retiredAt()).contains(NOW))),
                Arguments.of(
                        RecordState.ACTIVE,
                        "add a vehicle there",
                        addVehicleThere,
                        Expected.accepted(
                                r -> assertThat(r.standingOf(NEW_VEHICLE)).isEqualTo(Standing.ACTIVE))),
                Arguments.of(RecordState.RETIRED, "change", change, Expected.refused(new UnknownDepot(EAST))),
                Arguments.of(RecordState.RETIRED, "change hours", hours, Expected.refused(new UnknownDepot(EAST))),
                Arguments.of(RecordState.RETIRED, "retire", retire, Expected.refused(new UnknownDepot(EAST))),
                Arguments.of(
                        RecordState.RETIRED,
                        "add a vehicle there",
                        addVehicleThere,
                        Expected.refused(new UnknownDepot(EAST))));
    }

    @ParameterizedTest(name = "{0} depot, {1}")
    @MethodSource("depotLifecycle")
    void GivenADepotInEachState_WhenEachCommandRuns_ThenItTakesTheAllowedResultOrRefusesWithTheNamedError(
            RecordState state, String name, Command command, Expected expected) {
        FleetRegister register = withDepotIn(state);
        runAndCheck(register, command, expected);
    }

    private static void runAndCheck(FleetRegister register, Command command, Expected expected) {
        if (expected.error() != null) {
            assertRefusedUnchanged(register, () -> command.run(register), expected.error());
        } else {
            command.run(register);
            Objects.requireNonNull(expected.check()).accept(register);
            assertThat(register.pendingEvents()).isEmpty();
        }
    }
}
