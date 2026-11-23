package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.solution.Violation;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link Violation} is stored in, a row of its owner's violation table (ADR-127
 * decision 3). Every part but the rule is optional, which an embeddable can't hold, so each is a
 * nullable column, and {@link #violation()} builds the public record every caller sees. A failed
 * {@link SolveJob} stores its violations this way; it is package-private, not nested, so that every
 * aggregate of this package can.
 */
@Embeddable
class StoredViolation {

    private RuleCode rule;

    @Column(name = "route_id")
    private @Nullable RouteId route;

    // PostgreSQL reserves "order".
    @Column(name = "order_id")
    private @Nullable OrderId order;

    @Column(name = "vehicle_id")
    private @Nullable VehicleId vehicle;

    @Column(name = "driver_id")
    private @Nullable AccountId driver;

    @Column(name = "exceeded_by")
    private @Nullable Duration by;

    private @Nullable BigDecimal kilograms;

    private @Nullable BigDecimal litres;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredViolation() {}

    /**
     * Stores a violation.
     *
     * @param violation the violation
     */
    StoredViolation(Violation violation) {
        this.rule = violation.rule();
        this.route = violation.route().orElse(null);
        this.order = violation.order().orElse(null);
        this.vehicle = violation.vehicle().orElse(null);
        this.driver = violation.driver().orElse(null);
        this.by = violation.by().orElse(null);
        this.kilograms = violation.kilograms().orElse(null);
        this.litres = violation.litres().orElse(null);
    }

    /**
     * The violation these columns store.
     *
     * @return the violation
     */
    Violation violation() {
        return new Violation(
                rule,
                Optional.ofNullable(route),
                Optional.ofNullable(order),
                Optional.ofNullable(vehicle),
                Optional.ofNullable(driver),
                Optional.ofNullable(by),
                Optional.ofNullable(kilograms),
                Optional.ofNullable(litres));
    }
}
