package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link Pin} is stored in, a row of its plan's pin table (ADR-127 decision 3). Its
 * {@link PinTarget} is sealed, so the target is a vehicle column and a driver column, of which the
 * table's {@code CHECK} keeps exactly one, and {@link #pin()} builds the public record every caller
 * sees.
 */
@Embeddable
class StoredPin {

    // PostgreSQL reserves "order".
    @Column(name = "order_id")
    private OrderId order;

    @Column(name = "vehicle_id")
    private @Nullable VehicleId vehicle;

    @Column(name = "driver_id")
    private @Nullable AccountId driver;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredPin() {}

    /**
     * Stores a pin.
     *
     * @param pin the pin
     */
    StoredPin(Pin pin) {
        this.order = pin.order();
        switch (pin.target()) {
            case PinTarget.ToVehicle toVehicle -> this.vehicle = toVehicle.vehicle();
            case PinTarget.ToDriver toDriver -> this.driver = toDriver.driver();
        }
    }

    /**
     * The pin these columns store.
     *
     * @return the pin
     */
    Pin pin() {
        PinTarget target = vehicle != null
                ? new PinTarget.ToVehicle(vehicle)
                : new PinTarget.ToDriver(Objects.requireNonNull(driver, "a pin names a vehicle or a driver"));
        return new Pin(order, target);
    }
}
