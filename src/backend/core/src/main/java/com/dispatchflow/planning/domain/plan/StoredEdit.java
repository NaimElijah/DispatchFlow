package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link Edit} of a plan's journal is stored in, a row of its plan's undo or redo
 * table (ADR-127 decision 3). An edit is sealed, so it is stored as a kind beside nullable columns
 * for each kind's parts, and {@link #edit()} builds the public record every caller sees:
 *
 * <ul>
 *   <li>the stop a move, a removal or a placement names, and the order and attempt a removal or a
 *       placement names, or the order a pin names;
 *   <li>the route and position a move or a removal left, and the route and position a move or a
 *       placement joined; the route a lock names;
 *   <li>a removed stop's planned times, as {@link StoredStopPlan}'s columns;
 *   <li>the reason a placed order was dropped for, as {@link StoredDropReason}'s columns;
 *   <li>a pin's target before and after, each a vehicle or a driver, and a lock before and after.
 * </ul>
 *
 * <p>The tables' {@code CHECK} constraints keep each kind's columns present exactly when it has
 * them.
 */
@Embeddable
class StoredEdit {

    /** Which {@link Edit} an entry is, one constant per record. */
    private enum Kind {
        MOVED_STOP,
        TOOK_OFF,
        PLACED,
        PINNED,
        UNPINNED,
        LOCKED,
        UNLOCKED
    }

    @Enumerated(EnumType.STRING)
    private Kind kind;

    @Column(name = "stop_id")
    private @Nullable StopId stop;

    // PostgreSQL reserves "order".
    @Column(name = "order_id")
    private @Nullable OrderId order;

    private @Nullable Attempt attempt;

    @Column(name = "from_route_id")
    private @Nullable RouteId fromRoute;

    private @Nullable Integer fromPosition;

    @Column(name = "to_route_id")
    private @Nullable RouteId toRoute;

    private @Nullable Integer toPosition;

    @Column(name = "route_id")
    private @Nullable RouteId lockedRoute;

    @Embedded
    private @Nullable StoredStopPlan plan;

    @Embedded
    private @Nullable StoredDropReason droppedFor;

    @Column(name = "pin_before_vehicle_id")
    private @Nullable VehicleId pinBeforeVehicle;

    @Column(name = "pin_before_driver_id")
    private @Nullable AccountId pinBeforeDriver;

    @Column(name = "pin_after_vehicle_id")
    private @Nullable VehicleId pinAfterVehicle;

    @Column(name = "pin_after_driver_id")
    private @Nullable AccountId pinAfterDriver;

    @Enumerated(EnumType.STRING)
    private @Nullable RouteLock lockBefore;

    @Enumerated(EnumType.STRING)
    private @Nullable RouteLock lockAfter;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredEdit() {}

    /**
     * Stores an edit.
     *
     * @param edit the edit
     */
    StoredEdit(Edit edit) {
        this.kind = switch (edit) {
            case Edit.MovedStop moved -> {
                this.stop = moved.stop();
                this.fromRoute = moved.fromRoute();
                this.fromPosition = moved.fromPosition();
                this.toRoute = moved.toRoute();
                this.toPosition = moved.toPosition();
                yield Kind.MOVED_STOP;
            }
            case Edit.TookOff took -> {
                this.stop = took.stop();
                this.order = took.order();
                this.attempt = took.attempt();
                this.fromRoute = took.route();
                this.fromPosition = took.position();
                this.plan = took.plan().map(StoredStopPlan::new).orElse(null);
                yield Kind.TOOK_OFF;
            }
            case Edit.Placed placed -> {
                this.stop = placed.stop();
                this.order = placed.order();
                this.attempt = placed.attempt();
                this.toRoute = placed.route();
                this.toPosition = placed.position();
                this.droppedFor = new StoredDropReason(placed.before());
                yield Kind.PLACED;
            }
            case Edit.Pinned pinned -> {
                this.order = pinned.order();
                pinned.before().ifPresent(this::storePinBefore);
                storePinAfter(pinned.after());
                yield Kind.PINNED;
            }
            case Edit.Unpinned unpinned -> {
                this.order = unpinned.order();
                storePinBefore(unpinned.before());
                yield Kind.UNPINNED;
            }
            case Edit.Locked locked -> {
                this.lockedRoute = locked.route();
                this.lockBefore = locked.before().orElse(null);
                this.lockAfter = locked.after();
                yield Kind.LOCKED;
            }
            case Edit.Unlocked unlocked -> {
                this.lockedRoute = unlocked.route();
                this.lockBefore = unlocked.before();
                yield Kind.UNLOCKED;
            }
        };
    }

    /**
     * The edit these columns store.
     *
     * @return the edit
     * @throws com.dispatchflow.shared.StoredStateInvalid when a removed stop's stored times or a
     *     placed order's stored drop fact is refused by its own rules
     */
    Edit edit() {
        return switch (kind) {
            case MOVED_STOP ->
                new Edit.MovedStop(
                        stored(stop), stored(fromRoute), stored(fromPosition), stored(toRoute), stored(toPosition));
            case TOOK_OFF ->
                new Edit.TookOff(
                        stored(stop),
                        stored(order),
                        stored(attempt),
                        stored(fromRoute),
                        stored(fromPosition),
                        Optional.ofNullable(plan).map(StoredStopPlan::stopPlan));
            case PLACED ->
                new Edit.Placed(
                        stored(order),
                        stored(attempt),
                        stored(stop),
                        stored(toRoute),
                        stored(toPosition),
                        stored(droppedFor).dropReason());
            case PINNED ->
                new Edit.Pinned(
                        stored(order),
                        target(pinBeforeVehicle, pinBeforeDriver),
                        target(pinAfterVehicle, pinAfterDriver).orElseThrow());
            case UNPINNED ->
                new Edit.Unpinned(
                        stored(order), target(pinBeforeVehicle, pinBeforeDriver).orElseThrow());
            case LOCKED -> new Edit.Locked(stored(lockedRoute), Optional.ofNullable(lockBefore), stored(lockAfter));
            case UNLOCKED -> new Edit.Unlocked(stored(lockedRoute), stored(lockBefore));
        };
    }

    private void storePinBefore(PinTarget target) {
        switch (target) {
            case PinTarget.ToVehicle toVehicle -> this.pinBeforeVehicle = toVehicle.vehicle();
            case PinTarget.ToDriver toDriver -> this.pinBeforeDriver = toDriver.driver();
        }
    }

    private void storePinAfter(PinTarget target) {
        switch (target) {
            case PinTarget.ToVehicle toVehicle -> this.pinAfterVehicle = toVehicle.vehicle();
            case PinTarget.ToDriver toDriver -> this.pinAfterDriver = toDriver.driver();
        }
    }

    private static Optional<PinTarget> target(@Nullable VehicleId vehicle, @Nullable AccountId driver) {
        if (vehicle != null) {
            return Optional.of(new PinTarget.ToVehicle(vehicle));
        }
        return Optional.ofNullable(driver).map(PinTarget.ToDriver::new);
    }

    private static <T> T stored(@Nullable T part) {
        return Objects.requireNonNull(part, "the edit's kind stores this part");
    }
}
