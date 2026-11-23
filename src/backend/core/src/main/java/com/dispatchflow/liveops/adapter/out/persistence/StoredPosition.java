package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.application.port.out.KeptPosition;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.RouteId;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One stored {@link KeptPosition}, a record appended and never changed (DL §3.2, ADR-127 decision
 * 6). The record has no ID, so its row is keyed by a random UUID that nothing outside the table
 * sees.
 */
@Entity
@Immutable
@Table(schema = "liveops", name = "kept_position")
class StoredPosition {

    @Id
    private UUID id;

    private RouteId route;

    private AccountId driver;

    @Embedded
    private GeoPoint at;

    private Instant recordedAt;

    private Instant receivedAt;

    /** For Hibernate. */
    protected StoredPosition() {}

    /**
     * Stores a position.
     *
     * @param id the row's key, which the adapter draws
     * @param position the position
     */
    StoredPosition(UUID id, KeptPosition position) {
        this.id = id;
        this.route = position.route();
        this.driver = position.driver();
        this.at = position.at();
        this.recordedAt = position.recordedAt();
        this.receivedAt = position.receivedAt();
    }

    /**
     * The position this row stores.
     *
     * @return the position
     */
    KeptPosition toRecord() {
        return new KeptPosition(route, driver, at, recordedAt, receivedAt);
    }
}
