package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * A vehicle a re-optimization withdraws mid-route, and why (FR-267, FR-271).
 *
 * <p>A proposal stores each one in a row of its own, through this canonical constructor: it only
 * checks that both parts are present, which the table's {@code NOT NULL} columns keep, so it needs no
 * instantiator of its own (ADR-126).
 *
 * @param vehicle the vehicle
 * @param cause why
 */
@Embeddable
public record VehicleWithdrawal(
        @Column(name = "vehicle_id") VehicleId vehicle,
        @Enumerated(EnumType.STRING) WithdrawalCause cause) {

    /** Checks both parts are present. */
    public VehicleWithdrawal {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(cause, "cause");
    }
}
