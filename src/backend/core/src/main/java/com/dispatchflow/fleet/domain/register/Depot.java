package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A depot, an entity of {@link FleetRegister}: changed only through the register's methods.
 *
 * <p>It is mapped for JPA (ADR-126): its point and its hours are each two nullable columns, null
 * together when the depot has none.
 */
@Entity
@Table(schema = "fleet", name = "depot")
class Depot {

    @Id
    private UUID id;

    private DepotName name;

    @Embedded
    private @Nullable GeoPoint location;

    @Embedded
    private @Nullable DepotHours hours;

    private @Nullable Instant retiredAt;

    /** For Hibernate, which sets the fields from the stored row. */
    protected Depot() {}

    Depot(DepotSnapshot stored) {
        this.id = stored.id().value();
        this.name = stored.name();
        this.location = stored.location().orElse(null);
        this.hours = stored.hours().orElse(null);
        this.retiredAt = stored.retiredAt().orElse(null);
    }

    DepotId id() {
        return DepotId.of(id);
    }

    boolean isActive() {
        return retiredAt == null;
    }

    void change(DepotName newName, Optional<GeoPoint> newLocation) {
        this.name = newName;
        this.location = newLocation.orElse(null);
    }

    void changeHours(Optional<DepotHours> newHours) {
        this.hours = newHours.orElse(null);
    }

    void retire(Instant now) {
        this.retiredAt = now;
    }

    DepotSnapshot snapshot() {
        return new DepotSnapshot(
                id(), name, Optional.ofNullable(location), Optional.ofNullable(hours), Optional.ofNullable(retiredAt));
    }
}
