package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A depot, an entity of {@link FleetRegister}: changed only through the register's methods. */
final class Depot {

    private final DepotId id;
    private DepotName name;
    private @Nullable GeoPoint location;
    private @Nullable DepotHours hours;
    private @Nullable Instant retiredAt;

    Depot(DepotSnapshot stored) {
        this.id = stored.id();
        this.name = stored.name();
        this.location = stored.location().orElse(null);
        this.hours = stored.hours().orElse(null);
        this.retiredAt = stored.retiredAt().orElse(null);
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
                id, name, Optional.ofNullable(location), Optional.ofNullable(hours), Optional.ofNullable(retiredAt));
    }
}
