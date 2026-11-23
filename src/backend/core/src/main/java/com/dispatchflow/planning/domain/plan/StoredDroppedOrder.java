package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
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
 * The columns one {@link DroppedOrder} is stored in, a row of its owner's drop table (ADR-127
 * decision 3). Its {@link DropReason} is sealed, so it is stored as a kind beside nullable columns
 * for each reason's fact, and {@link #droppedOrder()} builds the public record every caller sees.
 * The reasons share their columns where their facts have one shape: a pin's vehicle or driver and
 * whether it was out; the four capacity figures; and the limit in the day with the earliest time
 * compared with it, which a time window, a shift and a depot's hours all have. The table's {@code
 * CHECK} constraints keep each kind's columns present exactly when it has them. A solve job's
 * result and a proposal store their drops this way; it is package-private, not nested, so that every
 * aggregate of this package can.
 */
@Embeddable
class StoredDroppedOrder {

    /** Which {@link DropReason} a drop has, one constant per record. */
    private enum Reason {
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

    // PostgreSQL reserves "order".
    @Column(name = "order_id")
    private OrderId order;

    private Attempt attempt;

    @Enumerated(EnumType.STRING)
    private Reason reason;

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
    protected StoredDroppedOrder() {}

    /**
     * Stores a drop.
     *
     * @param drop the drop
     */
    StoredDroppedOrder(DroppedOrder drop) {
        this.order = drop.order();
        this.attempt = drop.attempt();
        this.reason = switch (drop.reason()) {
            case DropReason.MissingLocation missing -> Reason.MISSING_LOCATION;
            case DropReason.NoVehicleAvailable none -> Reason.NO_VEHICLE_AVAILABLE;
            case DropReason.UnreachableLocation unreachable -> Reason.UNREACHABLE_LOCATION;
            case DropReason.RemovedByDispatcher removed -> Reason.REMOVED_BY_DISPATCHER;
            case DropReason.PinNotHonoured pin -> {
                storePin(pin);
                yield Reason.PIN_NOT_HONOURED;
            }
            case DropReason.Capacity capacity -> {
                this.weight = capacity.weight();
                this.volume = capacity.volume();
                this.largestWeightCapacity = capacity.largestWeightCapacity();
                this.largestVolumeCapacity = capacity.largestVolumeCapacity();
                yield Reason.CAPACITY;
            }
            case DropReason.TimeWindow window -> {
                storeFact(window.windowEnd(), window.earliestArrival());
                yield Reason.TIME_WINDOW;
            }
            case DropReason.Shift shift -> {
                storeFact(shift.latestReturn(), shift.earliestReturn());
                yield Reason.SHIFT;
            }
            case DropReason.DepotHours depot -> {
                storeFact(depot.depotCloses(), depot.earliestReturn());
                yield Reason.DEPOT_HOURS;
            }
            case DropReason.VehicleLimit limit -> Reason.VEHICLE_LIMIT;
            case DropReason.Displaced displaced -> Reason.DISPLACED;
        };
    }

    /**
     * The drop these columns store.
     *
     * @return the drop
     * @throws StoredStateInvalid when the earliest time of a time window, shift or depot-hours drop
     *     is before its day
     */
    DroppedOrder droppedOrder() {
        return new DroppedOrder(order, attempt, dropReason());
    }

    private DropReason dropReason() {
        return switch (reason) {
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
