package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A vehicle, an entity of {@link FleetRegister}: only the register's methods change it, and its
 * getters read it.
 *
 * <p>It is mapped for JPA (ADR-126): its details are embedded in its row, and a change replaces
 * them whole.
 */
@Entity
@Table(schema = "fleet", name = "vehicle")
public class Vehicle {

    @Id
    private UUID id;

    @Embedded
    private VehicleDetails details;

    private @Nullable Instant retiredAt;

    /** For Hibernate, which sets the fields from the stored row. */
    protected Vehicle() {}

    /**
     * Creates an active vehicle; the rules are {@link FleetRegister}'s.
     *
     * @param id the vehicle's ID
     * @param details its name, plate, capacity and depots
     */
    Vehicle(VehicleId id, VehicleDetails details) {
        this.id = Objects.requireNonNull(id, "id").value();
        this.details = Objects.requireNonNull(details, "details");
    }

    /**
     * The vehicle's ID.
     *
     * @return its ID
     */
    public VehicleId id() {
        return VehicleId.of(id);
    }

    /**
     * What a dispatcher registered or last changed about the vehicle (FR-05, FR-131).
     *
     * @return its name, plate, capacity and depots
     */
    public VehicleDetails details() {
        return details;
    }

    /**
     * When the vehicle was retired (BR-43).
     *
     * @return the time, or empty while it is active
     */
    public Optional<Instant> retiredAt() {
        return Optional.ofNullable(retiredAt);
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
}
