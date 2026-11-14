package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A vehicle, an entity of {@link FleetRegister}: changed only through the register's methods. */
final class Vehicle {

    private final VehicleId id;
    private VehicleDetails details;
    private @Nullable Instant retiredAt;

    Vehicle(VehicleSnapshot stored) {
        this.id = stored.id();
        this.details = stored.details();
        this.retiredAt = stored.retiredAt().orElse(null);
    }

    VehicleId id() {
        return id;
    }

    VehicleDetails details() {
        return details;
    }

    boolean isActive() {
        return retiredAt == null;
    }

    boolean uses(DepotId depot) {
        return details.startDepot().equals(depot) || details.endDepot().equals(depot);
    }

    void change(VehicleDetails newDetails) {
        this.details = newDetails;
    }

    void retire(Instant now) {
        this.retiredAt = now;
    }

    void reactivate() {
        this.retiredAt = null;
    }

    VehicleSnapshot snapshot() {
        return new VehicleSnapshot(id, details, Optional.ofNullable(retiredAt));
    }
}
