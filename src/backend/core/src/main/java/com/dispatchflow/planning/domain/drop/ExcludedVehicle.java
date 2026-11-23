package com.dispatchflow.planning.domain.drop;

import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * A vehicle a solve left out, and why, so the dispatcher sees it named (FR-197). The date's
 * exclusions are also the fact of a {@link DropReason.NoVehicleAvailable} drop (ADR-100).
 *
 * <p>A solve job's result and a proposal store each one in a row of its own, through this canonical
 * constructor: it only checks that both parts are present, which the table's {@code NOT NULL}
 * columns keep, so it needs no instantiator of its own (ADR-126).
 *
 * @param vehicle the vehicle
 * @param cause why it was left out
 */
@Embeddable
public record ExcludedVehicle(
        @Column(name = "vehicle_id") VehicleId vehicle,
        @Enumerated(EnumType.STRING) ExclusionCause cause) {

    /** Checks both parts are present. */
    public ExcludedVehicle {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(cause, "cause");
    }
}
