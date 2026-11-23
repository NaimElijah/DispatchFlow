package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
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
 * A depot, an entity of {@link FleetRegister}: only the register's methods change it, and its
 * getters read it.
 *
 * <p>It is mapped for JPA (ADR-126): its point and its hours are each two nullable columns, null
 * together when the depot has none.
 */
@Entity
@Table(schema = "fleet", name = "depot")
public class Depot {

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

    /**
     * Creates an active depot; the rules are {@link FleetRegister}'s.
     *
     * @param id the depot's ID
     * @param name its name
     * @param location its coordinates, if known
     * @param hours its opening hours, if it has any
     */
    Depot(DepotId id, DepotName name, Optional<GeoPoint> location, Optional<DepotHours> hours) {
        this.id = Objects.requireNonNull(id, "id").value();
        this.name = Objects.requireNonNull(name, "name");
        this.location = Objects.requireNonNull(location, "location").orElse(null);
        this.hours = Objects.requireNonNull(hours, "hours").orElse(null);
    }

    /**
     * The depot's ID.
     *
     * @return its ID
     */
    public DepotId id() {
        return DepotId.of(id);
    }

    /**
     * The depot's name (FR-04).
     *
     * @return its name
     */
    public DepotName name() {
        return name;
    }

    /**
     * Where the depot is (FR-130).
     *
     * @return its coordinates; empty for a depot that has none yet, which starts and ends no route
     *     (FR-197)
     */
    public Optional<GeoPoint> location() {
        return Optional.ofNullable(location);
    }

    /**
     * The depot's opening hours (FR-129).
     *
     * @return its hours, or empty for a depot with none
     */
    public Optional<DepotHours> hours() {
        return Optional.ofNullable(hours);
    }

    /**
     * When the depot was retired (BR-43).
     *
     * @return the time, or empty while it is active
     */
    public Optional<Instant> retiredAt() {
        return Optional.ofNullable(retiredAt);
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
}
