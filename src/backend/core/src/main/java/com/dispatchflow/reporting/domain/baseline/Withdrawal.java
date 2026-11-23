package com.dispatchflow.reporting.domain.baseline;

import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * A vehicle a revision withdrew mid-route, and why (FR-267, FR-271).
 *
 * <p>A {@link Baseline} stores each one in a row of its own, through this canonical constructor:
 * it only checks that both parts are present, which the table's {@code NOT NULL} columns keep, so
 * it needs no instantiator of its own (ADR-126).
 *
 * @param vehicle the withdrawn vehicle
 * @param cause why it was withdrawn, which decides how the baseline's solve treats it
 */
@Embeddable
public record Withdrawal(
        VehicleId vehicle, @Enumerated(EnumType.STRING) WithdrawalCause cause) {

    /** Checks both parts are present. */
    public Withdrawal {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(cause, "cause");
    }
}
