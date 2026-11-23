package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link DropReason} is stored in (ADR-127 decision 3). The reason is sealed, so it
 * is stored as a kind beside nullable columns for each reason's fact, and {@link #dropReason()}
 * builds the public record every caller sees. The reasons share their columns where their facts
 * have one shape: a pin's vehicle or driver and whether it was out; the four capacity figures; and
 * the limit in the day with the earliest time compared with it, which a time window, a shift and a
 * depot's hours all have. The tables' {@code CHECK} constraints keep each kind's columns present
 * exactly when it has them.
 *
 * <p>A drop stores its reason this way through {@link StoredDroppedOrder}, and a placement in a
 * plan's journal stores the reason the order was dropped for, which undoing it restores.
 */
@Embeddable
class StoredDropReason {

    /** Which {@link DropReason} a drop has, one constant per record. */
    private enum Kind {
        MISSING_LOCATION,
        NO_VEHICLE_AVAILABLE,
        UNREACHABLE_LOCATION,
        REMOVED_BY_DISPATCHER,
        PIN_NOT_HONOURED,
        CAPACITY,
        TIME_WINDOW,
        SHIFT,
        DEPOT_HOURS,
        VEHICLE_LIMIT,
        DISPLACED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    private Kind kind;

    @Column(name = "pin_vehicle_id")
    private @Nullable VehicleId pinVehicle;

    @Column(name = "pin_driver_id")
    private @Nullable AccountId pinDriver;

    private @Nullable Boolean pinTargetOut;

    private @Nullable Weight weight;

    private @Nullable Volume volume;

    private @Nullable Weight largestWeightCapacity;

    private @Nullable Volume largestVolumeCapacity;

    @Embedded
    @AttributeOverride(name = "date", column = @Column(name = "fact_limit_date"))
    @AttributeOverride(name = "seconds", column = @Column(name = "fact_limit_seconds"))
    private @Nullable DaySeconds factLimit;

    private @Nullable DeliveryDate earliestDate;

    private @Nullable Long earliestSeconds;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredDropReason() {}

    /**
     * Stores a reason.
     *
     * @param reason the reason
     */
    StoredDropReason(DropReason reason) {
        this.kind = switch (reason) {
            case DropReason.MissingLocation missing -> Kind.MISSING_LOCATION;
            case DropReason.NoVehicleAvailable none -> Kind.NO_VEHICLE_AVAILABLE;
            case DropReason.UnreachableLocation unreachable -> Kind.UNREACHABLE_LOCATION;
            case DropReason.RemovedByDispatcher removed -> Kind.REMOVED_BY_DISPATCHER;
            case DropReason.PinNotHonoured pin -> {
                storePin(pin);
                yield Kind.PIN_NOT_HONOURED;
            }
            case DropReason.Capacity capacity -> {
                this.weight = capacity.weight();
                this.volume = capacity.volume();
                this.largestWeightCapacity = capacity.largestWeightCapacity();
                this.largestVolumeCapacity = capacity.largestVolumeCapacity();
                yield Kind.CAPACITY;
            }
            case DropReason.TimeWindow window -> {
                storeFact(window.windowEnd(), window.earliestArrival());
                yield Kind.TIME_WINDOW;
            }
            case DropReason.Shift shift -> {
                storeFact(shift.latestReturn(), shift.earliestReturn());
                yield Kind.SHIFT;
            }
            case DropReason.DepotHours depot -> {
                storeFact(depot.depotCloses(), depot.earliestReturn());
                yield Kind.DEPOT_HOURS;
            }
            case DropReason.VehicleLimit limit -> Kind.VEHICLE_LIMIT;
            case DropReason.Displaced displaced -> Kind.DISPLACED;
        };
    }

    /**
     * The reason these columns store.
     *
     * @return the reason
     * @throws StoredStateInvalid with {@link DroppedOrder} as its subject when the earliest time of a
     *     time window, shift or depot-hours drop is before its day
     */
    DropReason dropReason() {
        return switch (kind) {
            case MISSING_LOCATION -> new DropReason.MissingLocation();
            case NO_VEHICLE_AVAILABLE -> new DropReason.NoVehicleAvailable();
            case UNREACHABLE_LOCATION -> new DropReason.UnreachableLocation();
            case REMOVED_BY_DISPATCHER -> new DropReason.RemovedByDispatcher();
            case PIN_NOT_HONOURED ->
                new DropReason.PinNotHonoured(
                        pin(), Objects.requireNonNull(pinTargetOut, "a pin drop says if it was out"));
            case CAPACITY ->
                new DropReason.Capacity(
                        Objects.requireNonNull(weight, "weight"),
                        Objects.requireNonNull(volume, "volume"),
                        Objects.requireNonNull(largestWeightCapacity, "largestWeightCapacity"),
                        Objects.requireNonNull(largestVolumeCapacity, "largestVolumeCapacity"));
            case TIME_WINDOW -> new DropReason.TimeWindow(limit(), earliest());
            case SHIFT -> new DropReason.Shift(limit(), earliest());
            case DEPOT_HOURS -> new DropReason.DepotHours(limit(), earliest());
            case VEHICLE_LIMIT -> new DropReason.VehicleLimit();
            case DISPLACED -> new DropReason.Displaced();
        };
    }

    private void storePin(DropReason.PinNotHonoured pin) {
        switch (pin.pin()) {
            case PinTarget.ToVehicle toVehicle -> this.pinVehicle = toVehicle.vehicle();
            case PinTarget.ToDriver toDriver -> this.pinDriver = toDriver.driver();
        }
        this.pinTargetOut = pin.targetOut();
    }

    private void storeFact(DaySeconds limit, SolveTime earliest) {
        this.factLimit = limit;
        this.earliestDate = earliest.date();
        this.earliestSeconds = earliest.seconds();
    }

    private PinTarget pin() {
        return pinVehicle != null
                ? new PinTarget.ToVehicle(pinVehicle)
                : new PinTarget.ToDriver(Objects.requireNonNull(pinDriver, "a pin drop names a vehicle or a driver"));
    }

    private DaySeconds limit() {
        return Objects.requireNonNull(factLimit, "the drop's fact has a limit");
    }

    private SolveTime earliest() {
        DeliveryDate date = Objects.requireNonNull(earliestDate, "the drop's fact has an earliest time");
        long seconds = Objects.requireNonNull(earliestSeconds, "the drop's fact has an earliest time");
        return StoredStateInvalid.whileRestoring(DroppedOrder.class, () -> SolveTime.of(date, seconds));
    }
}
