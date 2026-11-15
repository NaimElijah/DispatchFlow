package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.VehicleId;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * The refusals of the Fleet context (DL §4.2). Their fields become a response's parameters
 * (ADR-043), so they name records by ID and never carry a driver's name or phone, a plate or any
 * other text.
 */
public sealed interface FleetError extends DomainError {

    /**
     * Adding or reactivating a vehicle would make more active vehicles than the limit (BR-36,
     * NFR-04).
     *
     * @param limit the most active vehicles the register holds
     */
    record FleetFull(int limit) implements FleetError {}

    /**
     * Another active vehicle already has this display name, or one that looks the same: names are
     * compared without variation selectors or format characters, and with each run of whitespace
     * as one space (BR-48).
     *
     * @param usedBy the active vehicle that has it
     */
    record DuplicateVehicleName(VehicleId usedBy) implements FleetError {}

    /**
     * Another active vehicle already has this plate, after normalizing (BR-48, ADR-103).
     *
     * @param usedBy the active vehicle that has it
     */
    record DuplicatePlate(VehicleId usedBy) implements FleetError {}

    /**
     * No active depot has this ID: it was never added, or it is retired (BR-40).
     *
     * @param depot the depot asked for
     */
    record UnknownDepot(DepotId depot) implements FleetError {}

    /**
     * No vehicle in the register has this ID.
     *
     * @param vehicle the vehicle asked for
     */
    record UnknownVehicle(VehicleId vehicle) implements FleetError {}

    /**
     * The vehicle is not active: it is retired, or the register doesn't hold it. So it can't be
     * changed, retired again, rostered or marked unavailable (BR-43, DL §4.2).
     *
     * @param vehicle the vehicle
     */
    record VehicleNotActive(VehicleId vehicle) implements FleetError {}

    /**
     * Active vehicles still start or end at the depot, so it can't be retired (BR-40).
     *
     * @param depot the depot
     * @param vehicles the active vehicles that use it, in register order
     */
    record DepotHasActiveVehicles(DepotId depot, List<VehicleId> vehicles) implements FleetError {

        /** Copies the list. */
        public DepotHasActiveVehicles {
            Objects.requireNonNull(depot, "depot");
            vehicles = List.copyOf(vehicles);
        }
    }

    /**
     * An uncancelled plan for today or a later date uses the depot, so it can't be retired
     * (FR-305).
     *
     * @param depot the depot
     * @param plan the plan that uses it
     * @param date that plan's date
     */
    record DepotInUse(DepotId depot, PlanId plan, DeliveryDate date) implements FleetError {}

    /**
     * An uncancelled plan for today or a later date uses the vehicle, so it can't be retired
     * (FR-305).
     *
     * @param vehicle the vehicle
     * @param plan the plan that uses it
     * @param date that plan's date
     */
    record VehicleInUse(VehicleId vehicle, PlanId plan, DeliveryDate date) implements FleetError {}

    /**
     * An uncancelled plan for today or a later date uses the driver, so they can't be retired
     * (FR-305).
     *
     * @param driver the driver
     * @param plan the plan that uses them
     * @param date that plan's date
     */
    record DriverInUse(AccountId driver, PlanId plan, DeliveryDate date) implements FleetError {}

    /**
     * The person is not an active driver: their account is not active or lacks the Driver role, or
     * their driver record is retired. The same code means the same in Planning.
     *
     * @param driver the driver's account
     */
    record NotAnActiveDriver(AccountId driver) implements FleetError {}

    /**
     * A driver is anonymized only a year after retirement (BR-98, NFR-184, ADR-075). The same code
     * means the same in the other contexts that anonymize.
     */
    record RetentionNotOver() implements FleetError {}

    /**
     * On this date another driver already drives the vehicle (DL §4.2).
     *
     * @param vehicle the vehicle
     * @param driver the driver who drives it
     */
    record VehicleAlreadyAssigned(VehicleId vehicle, AccountId driver) implements FleetError {}

    /**
     * On this date the driver already drives another vehicle (DL §4.2).
     *
     * @param driver the driver
     * @param vehicle the vehicle they drive
     */
    record DriverAlreadyHasVehicle(AccountId driver, VehicleId vehicle) implements FleetError {}

    /**
     * The driver has no shift on this date's roster (FR-54).
     *
     * @param driver the driver
     */
    record DriverNotRostered(AccountId driver) implements FleetError {}

    /**
     * The driver is marked unavailable on this date, so a route can't be handed to them (FR-82,
     * FR-302).
     *
     * @param driver the driver
     */
    record DriverMarkedUnavailable(AccountId driver) implements FleetError {}

    /**
     * A shift whose end is not after its start (FR-190).
     *
     * @param start the refused start
     * @param end the refused end
     */
    record InvalidShift(LocalTime start, LocalTime end) implements FleetError {}

    /**
     * Depot hours whose closing time is not after their opening time (FR-129, ADR-103).
     *
     * @param opens the refused opening time
     * @param closes the refused closing time
     */
    record InvalidDepotHours(LocalTime opens, LocalTime closes) implements FleetError {}

    /**
     * A plate that, once normalized, is not 1 to 10 ASCII letters or digits (ADR-103). It names no
     * text, because an error's fields never carry free text.
     */
    record InvalidPlate() implements FleetError {}

    /** A vehicle's display name that is empty once trimmed (FR-131). */
    record BlankVehicleName() implements FleetError {}

    /** A depot's name that is empty once trimmed. */
    record BlankDepotName() implements FleetError {}

    /** A driver's name that is empty once trimmed. */
    record BlankDriverName() implements FleetError {}
}
