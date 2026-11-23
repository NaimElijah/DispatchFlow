package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A vehicle, an entity of {@link FleetRegister}: changed only through the register's methods.
 *
 * <p>It is mapped for JPA (ADR-126): its details are embedded in its row, and a change replaces
 * them whole.
 */
@Entity
@Table(schema = "fleet", name = "vehicle")
class Vehicle {

    @Id
    private UUID id;

    @Embedded
    private VehicleDetails details;

    private @Nullable Instant retiredAt;

    /** For Hibernate, which sets the fields from the stored row. */
    protected Vehicle() {}

    Vehicle(VehicleSnapshot stored) {
        this.id = stored.id().value();
        this.details = stored.details();
        this.retiredAt = stored.retiredAt().orElse(null);
    }

    VehicleId id() {
        return VehicleId.of(id);
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
        return new VehicleSnapshot(id(), details, Optional.ofNullable(retiredAt));
    }
}
